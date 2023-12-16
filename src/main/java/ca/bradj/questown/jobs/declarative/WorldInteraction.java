package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.integration.minecraft.MCCoupledHeldItem;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
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
        extends AbstractWorldInteraction<MCExtra, BlockPos, MCTownItem, MCCoupledHeldItem> {

    public boolean tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCCoupledHeldItem> work,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryWorking(new MCExtra(town, work, entity), workSpot);
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
                ingredientQtyRequiredAtStates,
                timeRequiredAtStates,
                () -> journal.getItems().stream().map(MCHeldItem::get).toList(),
                new InventoryHandle<>() {
                    @Override
                    public Collection<MCCoupledHeldItem> getItems() {
                        ImmutableList.Builder<MCCoupledHeldItem> b = ImmutableList.builder();
                        for (int i = 0; i < inventory.getContainerSize(); i++) {
                            final int ii = i;
                            b.add(MCCoupledHeldItem.fromMCItemStack(inventory.getItem(i), (shrunk) -> {
                                inventory.setItem(ii, shrunk.toItem().toItemStack());
                            }));
                        }
                        return b.build();
                    }

                    @Override
                    public void set(
                            int ii,
                            MCCoupledHeldItem shrink
                    ) {
                        inventory.setItem(ii, shrink.get().toItemStack());
                        inventory.setChanged();
                    }
                }
        );
        this.inventory = inventory;
        this.journal = journal;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.workResult = workResult;
    }

    private static ImmutableMap<Integer, Function<MCTownItem, Boolean>> stripMC2(
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        ImmutableMap.Builder<Integer, Function<MCTownItem, Boolean>> b = ImmutableMap.builder();
        toolsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.toItemStack())));
        return b.build();
    }

    private static ImmutableMap<Integer, Function<MCCoupledHeldItem, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<MCCoupledHeldItem, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, z -> v.test(z.get().toItemStack())));
        return b.build();
    }

    @Override
    protected boolean tryExtractOre(
            MCExtra extra,
            BlockPos position
    ) {
        return tryExtractOre(extra.town(), extra.work(), position);
    }

    protected boolean tryExtractOre(
            TownInterface town,
            WorkStateContainer<BlockPos> jh,
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
            MCExtra extra,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        return tryProcessOre(extra.work(), extra.entity(), workSpot);
    }

    private boolean tryProcessOre(
            WorkStateContainer<BlockPos> sl,
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
                nextStepTime = 0;
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
        Function<MCTownItem, Boolean> tool = toolsRequiredAtStates.get(state);
        if (tool == null) {
            QT.JOB_LOGGER.warn("Tool requirement is null while trying to degrade tool");
            return;
        }

        Optional<MCTownItem> foundTool = journal.getItems()
                .stream()
                .map(MCHeldItem::get)
                .filter(tool::apply)
                .findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems().indexOf(foundTool.get());
            ItemStack is = foundTool.get().toItemStack();
            is.hurtAndBreak(1, entity, (x) -> {
            });
            journal.setItem(idx, MCHeldItem.fromMCItemStack(is));
        }
    }

    @Override
    protected WorkStateContainer<BlockPos> getWorkStatuses(MCExtra extra) {
        return extra.work();
    }

    @Override
    protected boolean canInsertItem(
            MCExtra mcExtra,
            MCCoupledHeldItem item,
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
