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

// TODO[ASAP]: Break ties to MC and unit test this
public class WorldInteraction implements WorkStatusStore.InsertionRules<ItemStack> {
    private final Marker marker = MarkerManager.getMarker("WI").addParents(MarkerManager.getMarker("Smelter"));

    // TODO: Can we deal with the inventory OR the journal (both causes confusion)
    private final Container inventory;
    private final ProductionJournal<MCTownItem, MCHeldItem> journal;
    private final int maxState;
    private final ImmutableMap<Integer, Function<ItemStack, Boolean>> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;
    private final ImmutableMap<Integer, Integer> timeRequiredAtStates;
    private final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;
    private final BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<ItemStack>> workResult;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final int interval;
    private int ticksSinceLastAction;

    public WorldInteraction(
            Container inventory,
            ProductionJournal<MCTownItem, MCHeldItem> journal,
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<ItemStack>> workResult,
            int interval
    ) {
        this.inventory = inventory;
        this.journal = journal;
        this.maxState = maxState;
        this.ingredientsRequiredAtStates = stripMC(ingredientsRequiredAtStates);
        this.ingredientQtyRequiredAtStates = ingredientQtyRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.timeRequiredAtStates = timeRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workResult = workResult;
        this.interval = interval;
    }

    private ImmutableMap<Integer, Function<ItemStack, Boolean>> stripMC(ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<Integer, Function<ItemStack, Boolean>> b = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> b.put(k, v::test));
        return b.build();
    }

    public boolean tryWorking(
            TownInterface town,
            LivingEntity entity,
            WorkSpot<Integer, BlockPos> workSpot
    ) {
        if (town.getServerLevel() == null) {
            return false;
        }
        WorkStatusHandle<BlockPos, ItemStack> jh = town.getWorkStatusHandle();

        ticksSinceLastAction++;
        if (ticksSinceLastAction < interval) {
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
            return tryExtractOre(town, workSpot.position);
        }

        if (this.workRequiredAtStates.containsKey(workSpot.action)) {
            Integer work = this.workRequiredAtStates.get(workSpot.action);
            if (work != null && work > 0) {
                if (workSpot.action == 0) {
                    WorkStatusStore.State jobBlockState = jh.getJobBlockState(workSpot.position);
                    if (jobBlockState == null) {
                        jobBlockState = new WorkStatusStore.State(0, 0, 0);
                    }
                    if (jobBlockState.workLeft() == 0) {
                        jh.setJobBlockState(workSpot.position, jobBlockState.setWorkLeft(work));
                    }
                }
                if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
                    tryInsertIngredients(jh, workSpot);
                }
                return tryProcessOre(jh, entity, workSpot);
            }
        }

        if (this.ingredientsRequiredAtStates.get(workSpot.action) != null) {
            return tryInsertIngredients(jh, workSpot);
        }

        return false;
    }

    protected boolean tryExtractOre(
            TownInterface town,
            BlockPos oldPos
    ) {
        WorkStatusHandle<BlockPos, ItemStack> jh = town.getWorkStatusHandle();
        @Nullable ServerLevel sl = town.getServerLevel();
        if (Integer.valueOf(maxState).equals(JobBlock.getState(jh, oldPos))) {
            @Nullable WorkStatusStore.State newState = JobBlock.extractRawProduct(
                    sl, jh, oldPos, this.workResult.apply(town.getServerLevel(), journal),
                    is -> journal.addItemIfSlotAvailable(MCHeldItem.fromMCItemStack(is))
            );
            return newState != null;
        }
        return false;
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
                QT.JOB_LOGGER.debug(marker, "Smelter removed {} from their inventory {}", name, invBefore);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<Integer, Function<ItemStack, Boolean>> ingredientsRequiredAtStates() {
        return ingredientsRequiredAtStates;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQtyRequiredAtStates;
    }
}
