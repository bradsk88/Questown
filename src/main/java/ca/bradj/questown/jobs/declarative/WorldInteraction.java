package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.SpecialRules;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WorldInteraction
        extends AbstractWorldInteraction<MCExtra, BlockPos, MCTownItem, MCHeldItem, Boolean> {

    private final ImmutableList<String> specialRules;

    public Boolean tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryWorking(new MCExtra(town, work, entity), workSpot);
    }

    private final Marker marker = MarkerManager.getMarker("WI");

    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;

    public WorldInteraction(
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            ImmutableList<String> specialRules,
            int interval
    ) {
        super(
                journal.getJobId(),
                -1, // Not used by this implementation
                interval,
                maxState,
                stripMC2(toolsRequiredAtStates),
                workRequiredAtStates,
                stripMC(ingredientsRequiredAtStates),
                ingredientQtyRequiredAtStates,
                timeRequiredAtStates
        );
        this.journal = journal;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.resultGenerator = resultGenerator;
        this.specialRules = specialRules;
    }

    private static ImmutableMap<Integer, Function<MCTownItem, Boolean>> stripMC2(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCTownItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.toItemStack())));
        return b.build();
    }

    private static ImmutableMap<Integer, Function<MCHeldItem, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<MCHeldItem, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.get().toItemStack())));
        return b.build();
    }

    @Override
    protected Boolean tryExtractOre(
            MCExtra extra,
            BlockPos position
    ) {
        return tryExtractOre(extra.town(), extra.work(), position);
    }

    protected Boolean tryExtractOre(
            TownInterface town,
            ImmutableWorkStateContainer<BlockPos, Boolean> jh,
            BlockPos pos
    ) {
        //TODO: Extract common implementation between this and MCTSWI
        @Nullable ServerLevel sl = town.getServerLevel();
        if (Integer.valueOf(maxState).equals(JobBlock.getState(jh::getJobBlockState, pos))) {
            AbstractWorkStatusStore.State after = JobBlock.extractRawProduct(
                    sl, jh, pos, this.resultGenerator.apply(town.getServerLevel(), journal.getItems()),
                    journal::addItemIfSlotAvailable, specialRules.contains(SpecialRules.NULLIFY_EXCESS_RESULTS)
            );
            if (after != null) {
                jh.setJobBlockState(pos, AbstractWorkStatusStore.State.fresh());
                return true;
            }
        }
        return null;
    }

    @Override
    protected Boolean setHeldItem(
            MCExtra uxtra,
            Boolean tuwn,
            int villagerIndex,
            int itemIndex,
            MCHeldItem item
    ) {
        journal.setItem(itemIndex, item);
        return true;
    }

    @Override
    protected Boolean degradeTool(
            MCExtra mcExtra,
            Boolean tuwn,
            Function<MCTownItem, Boolean> toolCheck
    ) {
        Optional<MCHeldItem> foundTool = journal.getItems()
                .stream()
                .filter(v -> toolCheck.apply(v.get()))
                .findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems().indexOf(foundTool.get());
            ItemStack is = foundTool.get().get().toItemStack();
            is.hurtAndBreak(1, mcExtra.entity(), (x) -> {
            });
            journal.setItem(idx, MCHeldItem.fromMCItemStack(is));
            return true;
        }
        return null;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, Boolean> getWorkStatuses(MCExtra extra) {
        return extra.work();
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            MCExtra mcExtra,
            int villagerIndex
    ) {
        return journal.getItems();
    }

    @Override
    protected boolean canInsertItem(
            MCExtra mcExtra,
            MCHeldItem item,
            BlockPos bp
    ) {
        return mcExtra.work().canInsertItem(item, bp);
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }

    @Override
    protected boolean isEntityClose(
            MCExtra extra,
            BlockPos position
    ) {
        return Jobs.isCloseTo(extra.entity().blockPosition(), position);
    }

    @Override
    protected boolean isReady(MCExtra extra) {
        return extra.town() != null && extra.town().getServerLevel() != null;
    }
}
