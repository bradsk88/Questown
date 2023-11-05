package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.TownJobHandle;
import ca.bradj.questown.town.interfaces.JobHandle;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public class WorldInteraction implements TownJobHandle.InsertionRules {
    private final Marker marker = MarkerManager.getMarker("WI").addParents(MarkerManager.getMarker("Smelter"));

    private final Container inventory;
    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final int maxState;
    private final ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;
    private final ItemStack workResult;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private int ticksSinceLastAction;

    public WorldInteraction(
            Container inventory,
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ItemStack workResult
    ) {
        this.inventory = inventory;
        this.journal = journal;
        this.maxState = maxState;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
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
        JobHandle jh = town.getJobHandle();

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
            return tryExtractOre(town.getServerLevel(), jh, workSpot.position);
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action)) {
            Integer work = this.workRequiredAtStates.get(workSpot.action);
            if (work != null && work > 0) {
                return tryProcessOre(jh, entity, workSpot);
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
            return tryInsertIngredients(jh, workSpot);
        }

        return false;
    }

    protected boolean tryExtractOre(
            ServerLevel sl,
            JobHandle jh,
            BlockPos oldPos
    ) {
        if (Integer.valueOf(maxState).equals(JobBlock.getState(jh, oldPos))) {
            @Nullable TownJobHandle.State newState = JobBlock.extractRawProduct(
                    sl, jh, oldPos, this.workResult,
                    is -> journal.addItemIfSlotAvailable(MCHeldItem.fromMCItemStack(is))
            );
            return newState != null;
        }
        return false;
    }

    private boolean tryProcessOre(
            JobHandle sl,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> ws
    ) {
        BlockPos bp = ws.position;
        @Nullable Integer s = JobBlock.getState(sl, bp);
        if (ws.action.equals(s)) {
            TownJobHandle.State blockState = JobBlock.applyWork(sl, bp);
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
            JobHandle sl,
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
            if (!sl.canInsertItem(item, bp)) {
                continue;
            }
            Integer nextStepWork = workRequiredAtStates.getOrDefault(
                    journal.getStatus().getProductionState() + 1, 0
            );
            if (nextStepWork == null) {
                nextStepWork = 0;
            }
            if (sl.tryInsertItem(this, item, bp, nextStepWork)) {
                QT.JOB_LOGGER.debug(marker, "Smelter removed {} from their inventory {}", name, invBefore);
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<Integer, Ingredient> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }
}
