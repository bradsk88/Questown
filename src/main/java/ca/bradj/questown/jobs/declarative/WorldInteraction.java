package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WorldInteraction extends AbstractWorldInteraction<WorldInteraction.Extra, BlockPos, ItemStack, MCHeldItem> {

    public boolean tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, ItemStack> work,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryWorking(new Extra(town, work, entity), workSpot);
    }

    public record Extra(
            TownInterface town,
            WorkStatusHandle<BlockPos, ItemStack> work,
            LivingEntity entity
    ) {
    }

    private final Marker marker = MarkerManager.getMarker("WI");

    // TODO: Can we deal with the inventory OR the journal (both causes confusion)
    private final Container inventory;
    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<MCHeldItem>> workResult;

    public WorldInteraction(
            Container inventory,
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<MCHeldItem>> workResult,
            int interval
    ) {
        super(
                interval,
                maxState,
                stripMC2(toolsRequiredAtStates),
                workRequiredAtStates,
                stripMC(ingredientsRequiredAtStates),
                journal
        );
        this.inventory = inventory;
        this.journal = journal;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.workResult = workResult;
    }

    private static ImmutableMap<Integer, Function<MCHeldItem, Boolean>> stripMC2(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCHeldItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.get().toItemStack())));
        return b.build();
    }

    private static ImmutableMap<Integer, Function<ItemStack, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<ItemStack, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, v::test));
        return b.build();
    }

    @Override
    protected boolean tryExtractOre(
            Extra extra,
            BlockPos position
    ) {
        return tryExtractOre(extra.town, extra.work, position);
    }

    protected boolean tryExtractOre(
            TownInterface town,
            WorkStatusHandle<BlockPos, ItemStack> jh,
            BlockPos oldPos
    ) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (Integer.valueOf(maxState).equals(JobBlock.getState(jh, oldPos))) {
            @Nullable WorkStatusStore.State newState = JobBlock.extractRawProduct(
                    sl, jh, oldPos, this.workResult.apply(town.getServerLevel(), journal),
                    journal::addItemIfSlotAvailable
            );
            return newState != null;
        }
        return false;
    }

    @Override
    protected boolean tryProcessOre(
            Extra extra,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryProcessOre(extra.work, extra.entity, workSpot);
    }

    private boolean tryProcessOre(
            WorkStatusHandle<BlockPos, ItemStack> sl,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> ws
    ) {
        BlockPos bp = ws.position;
        @Nullable Integer s = JobBlock.getState(sl, bp);
        if (ws.action.equals(s)) {
            Integer nextStepWork = workRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepWork == null) {
                nextStepWork = 0;
            }
            Integer nextStepTime = timeRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepTime == null) {
                nextStepWork = 0;
            }
            WorkStatusStore.State blockState = JobBlock.applyWork(sl, bp, nextStepWork, nextStepTime);
            boolean didWork = blockState != null;
            if (didWork && toolsRequiredAtStates.get(s) != null) {
                degradeTool(entity, JobBlock.getState(sl, bp));
            }
            return didWork;
        }
        return false;
    }

    private void degradeTool(
            LivingEntity entity,
            @Nullable Integer state
    ) {
        if (state == null) {
            QT.JOB_LOGGER.warn("Block state is null while trying to degrade tool");
            return;
        }
        Function<MCHeldItem, Boolean> tool = toolsRequiredAtStates.get(state);
        if (tool == null) {
            QT.JOB_LOGGER.warn("Tool requirement is null while trying to degrade tool");
            return;
        }

        Optional<MCHeldItem> foundTool = journal.getItems()
                .stream()
                .filter(tool::apply)
                .findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems().indexOf(foundTool.get());
            ItemStack is = foundTool.get().get().toItemStack();
            is.hurtAndBreak(1, entity, (x) -> {
            });
            journal.setItem(idx, MCHeldItem.fromMCItemStack(is));
        }
    }

    @Override
    protected boolean tryInsertIngredients(
            Extra extra,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryInsertIngredients(extra.work, workSpot);
    }

    private boolean tryInsertIngredients(
            WorkStatusHandle<BlockPos, ItemStack> sl,
            WorkSpot<Integer, BlockPos> ws
    ) {
        BlockPos bp = ws.position;
        Integer state = JobBlock.getState(sl, bp);
        if (state == null || !state.equals(ws.action)) {
            return false;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) {
                continue;
            }
            String invBefore = inventory.toString();
            String name = "[unknown]";
            ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(item.getItem());
            if (registryName != null) {
                name = registryName.toString();
            }
            if (!sl.canInsertItem(item, bp)) {
                continue;
            }
            Integer nextStepWork = workRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepWork == null) {
                nextStepWork = 0;
            }
            Integer nextStepTime = timeRequiredAtStates.getOrDefault(
                    ws.action + 1, 0
            );
            if (nextStepTime == null) {
                nextStepTime = 0;
            }
            if (sl.tryInsertItem(this, item, bp, nextStepWork, nextStepTime)) {
                QT.JOB_LOGGER.debug(marker, "Villager removed {} from their inventory {}", name, invBefore);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }

    @Override
    protected void setJobBlockState(
            Extra extra,
            BlockPos position,
            WorkStatusStore.State state
    ) {
        extra.work.setJobBlockState(position, state);
    }

    @Override
    protected WorkStatusStore.State getJobBlockState(
            Extra extra,
            BlockPos position
    ) {
        return extra.work.getJobBlockState(position);
    }

    @Override
    protected boolean isEntityClose(Extra extra, BlockPos position) {
        return Jobs.isCloseTo(extra.entity.blockPosition(), position);
    }

    @Override
    protected boolean isReady(Extra extra) {
        return extra.town != null && extra.town.getServerLevel() != null;
    }
}
