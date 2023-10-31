package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.ItemAccepting;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class WorldInteraction {
    private final Marker marker = MarkerManager.getMarker("WI").addParents(MarkerManager.getMarker("Smelter"));

    private final Container inventory;
    // FIXME: Build production journal
    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final int maxState;
    private final ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;
    private final ItemStack workResult;
    private int ticksSinceLastAction;

    public WorldInteraction(
            Container inventory,
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ItemStack workResult
    ) {
        this.inventory = inventory;
        this.journal = journal;
        this.maxState = maxState;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workResult = workResult;
    }

    public boolean tryWorking(
            TownInterface town,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }
        ServerLevel sl = town.getServerLevel();

        ticksSinceLastAction++;
        if (ticksSinceLastAction < Config.FARM_ACTION_INTERVAL.get()) { // TODO: Smelter specific config
            return false;
        }
        ticksSinceLastAction = 0;

        if (workSpot == null) {
            return false;
        }

        if (!Jobs.isCloseTo(entity.blockPosition(), workSpot.position)) {
            return false;
        }

        if (workSpot.action == maxState) {
            return tryExtractOre(sl, workSpot.position);
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action)) {
            Integer work = this.workRequiredAtStates.get(workSpot.action);
            if (work != null && work > 0) {
                return tryProcessOre(sl, entity, workSpot);
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
            return tryInsertIngredients(sl, workSpot);
        }

        return false;
    }

    private boolean tryExtractOre(
            ServerLevel sl,
            BlockPos oldPos
    ) {
        if (Integer.valueOf(maxState).equals(JobBlock.getState(sl, oldPos))) {
            @Nullable BlockState newState = JobBlock.extractRawProduct(
                    sl, oldPos, this.workResult,
                    is -> journal.addItemIfSlotAvailable(MCHeldItem.fromMCItemStack(is))
            );
            return newState != null;
        }
        return false;
    }

    private boolean tryProcessOre(
            ServerLevel sl,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> ws
    ) {
        BlockPos bp = ws.position;
        @Nullable Integer s = JobBlock.getState(sl, bp);
        if (ws.action.equals(s)) {
            BlockState blockState = JobBlock.applyWork(sl, bp);
            boolean didWork = blockState != null;
            if (didWork) {
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
        Ingredient tool = toolsRequiredAtStates.get(state);
        if (tool == null) {
            QT.JOB_LOGGER.warn("Tool requirement is null while trying to degrade tool");
            return;
        }

        Optional<MCHeldItem> foundTool = journal.getItems()
                .stream()
                .filter(v -> tool.test(v.get().toItemStack()))
                .findFirst();
        if (foundTool.isPresent()) {
            int idx = journal.getItems().indexOf(foundTool.get());
            ItemStack is = foundTool.get().get().toItemStack();
            is.hurtAndBreak(1, entity, (x) -> {
            });
            journal.setItem(idx, MCHeldItem.fromMCItemStack(is));
        }
    }

    private boolean tryInsertIngredients(
            ServerLevel sl,
            WorkSpot<Integer, BlockPos> ws
    ) {
        BlockPos bp = ws.position;
        Integer state = JobBlock.getState(sl, bp);
        if (state == null || !state.equals(ws.action)) {
            return false;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            String invBefore = inventory.toString();
            String name = "[unknown]";
            ResourceLocation registryName = item.getItem().getRegistryName();
            if (registryName != null) {
                name = registryName.toString();
            }
            BlockState bl = sl.getBlockState(bp);
            if (bl.getBlock() instanceof ItemAccepting ia) {
                Integer nextStepWork = workRequiredAtStates.getOrDefault(
                        journal.getStatus().getProductionState() + 1, 0
                );
                if (nextStepWork == null) {
                    nextStepWork = 0;
                }
                ia.insertItem(sl, bp, item, nextStepWork);
                if (item.getCount() > 0) {
                    // didn't insert successfully
                    return false;
                }
                QT.JOB_LOGGER.debug(marker, "Smelter removed {} from their inventory {}", name, invBefore);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
