package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.KnowledgeMetaItem;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends AbstractWorldInteraction<MCTownStateWorldInteraction.Inputs, BlockPos, MCTownItem, MCHeldItem, MCTownState> {

    public record Inputs(
            MCTownState town,
            ServerLevel level
    ) {
    }

    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;
    private final Map<Integer, Integer> ingredientQuantityRequiredAtStates;

    public MCTownStateWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator
    ) {
        super(
                jobId, villagerIndex, interval, maxState, Jobs.unMC(toolsRequiredAtStates),
                workRequiredAtStates, Jobs.unMCHeld(ingredientsRequiredAtStates),
                ingredientQuantityRequiredAtStates, timeRequiredAtStates
        );
        this.resultGenerator = resultGenerator;
        this.ingredientQuantityRequiredAtStates = ingredientQuantityRequiredAtStates;
    }

    @Override
    protected MCTownState setHeldItem(
            Inputs uxtra,
            MCTownState tuwn,
            int villagerIndex,
            int itemIndex,
            MCHeldItem item
    ) {
        if (tuwn == null) {
            tuwn = uxtra.town();
        }
        TownState.VillagerData<MCHeldItem> villager = tuwn.villagers.get(villagerIndex);
        return tuwn.withVillagerData(villagerIndex, villager.withSetItem(itemIndex, item));
    }

    @Override
    protected MCTownState degradeTool(
            Inputs mcTownState,
            @Nullable MCTownState tuwn,
            Function<MCTownItem, Boolean> isExpectedTool
    ) {
        if (tuwn == null) {
            tuwn = mcTownState.town();
        }

        int i = -1;
        for (MCHeldItem item : this.getHeldItems(mcTownState, villagerIndex)) {
            i++;
            if (!isExpectedTool.apply(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toItemStack();
            itemStack.hurt(1, mcTownState.level.getRandom(), null);
            if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                itemStack = ItemStack.EMPTY;
            }
            return setHeldItem(mcTownState, tuwn, villagerIndex, i, MCHeldItem.fromMCItemStack(itemStack));
        }
        return tuwn;
    }

    @Override
    protected boolean canInsertItem(
            Inputs mcTownState,
            MCHeldItem item,
            BlockPos bp
    ) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(Inputs mcTownState) {
        return mcTownState.town();
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            Inputs mcTownState,
            int villagerIndex
    ) {
        return ProductionTimeWarper.getHeldItems(mcTownState.town(), villagerIndex);
    }

    @Override
    protected MCTownState tryExtractOre(
            @NotNull Inputs inputs,
            BlockPos position
    ) {
        AbstractWorkStatusStore.State s = getJobBlockState(inputs, position);
        if (s != null && s.processingState() == maxState) {

            Collection<MCHeldItem> items = getHeldItems(inputs, villagerIndex);
            Iterable<MCHeldItem> generatedResult = resultGenerator.apply(inputs.level, items);

            Stack<MCHeldItem> stack = new Stack<>();
            generatedResult.forEach(stack::push);

            if (stack.isEmpty()) {
                QT.JOB_LOGGER.error(
                        "No results during extraction phase. That's probably a bug. Town State: {}",
                        inputs.town()
                );
                return inputs.town();
            }

            MCTownState ts = inputs.town();
            int i = -1;
            for (MCHeldItem item : items) {
                i++;
                if (!item.isEmpty()) {
                    continue;
                }
                MCHeldItem newItem = stack.pop();
                if (newItem.get().toItemStack().getCount() > 1) {
                    stack.push(newItem.shrink());
                }
                if (newItem.get().get() instanceof KnowledgeMetaItem) {
                    ts = ts.withKnowledge(newItem);
                }else {
                    ts = setHeldItem(inputs, ts, villagerIndex, i, newItem.unit());
                }
                ts = ts.setJobBlockState(position, AbstractWorkStatusStore.State.fresh());


                if (stack.isEmpty()) {
                    return ts;
                }
            }
            // TODO: If SpecialRules.NULLIFY_EXCESS_RESULTS does not apply, should we spawn items in town?
        }
        return null;
    }

    @Override
    protected boolean isEntityClose(
            Inputs mcTownState,
            BlockPos position
    ) {
        return true;
    }

    @Override
    protected boolean isReady(Inputs mcTownState) {
        return true;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return ingredientQuantityRequiredAtStates;
    }

    public void injectTicks(int interval) {
        ticksSinceLastAction += interval;
    }

    public JobTownProvider<MCRoom> asTownJobs(
            @NotNull AbstractWorkStatusStore.State workStates,
            MCRoom mcRoom,
            BlockPos roomBlock,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        return new JobTownProvider<MCRoom>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                if (workStates.processingState() == maxState) {
                    return ImmutableList.of(mcRoom);
                }
                return ImmutableList.of();
            }

            @Override
            public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsByState() {
                int curState = workStates.processingState();
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(curState);
                if (ings != null) {
                    return ImmutableMap.of(curState, ImmutableList.of(mcRoom));
                }

                Function<MCTownItem, Boolean> toolChk = toolsRequiredAtStates.get(curState);
                if (toolChk != null) {
                    return ImmutableMap.of(curState, ImmutableList.of(mcRoom));
                }

                if (workStates.workLeft() > 0) {
                    return ImmutableMap.of(curState, ImmutableList.of(mcRoom));
                }

                return ImmutableMap.of();
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return false;
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> ImmutableList.of(
                                () -> ImmutableList.of(roomBlock)
                        ),
                        bp -> workStates
                );
                ImmutableList.Builder<Integer> b = ImmutableList.builder();
                statesWithUnfinishedWork.forEach(
                        state -> {
                            if (toolsRequiredAtStates.get(state) == null) {
                                b.add(state);
                            }
                        }
                );
                return b.build();
            }

            @Override
            public boolean hasSupplies() {
                // TODO: Reduce deuplication with DeclarativeJob.roomsNeedingIngredientsOrTools
                int curState = workStates.processingState();
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(curState);
                if (ings != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(i -> ings.apply(MCHeldItem.fromTown(i)))) {
                            return true;
                        }
                    }
                }
                Function<MCTownItem, Boolean> toolChk = toolsRequiredAtStates.get(curState);
                if (toolChk != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(toolChk::apply)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public boolean hasSpace() {
                return containers.stream().anyMatch(v -> !v.isFull());
            }
        }

                ;
    }

    public EntityInvStateProvider<Integer> asInventory(
            Supplier<Collection<MCHeldItem>> heldItems,
            Supplier<Integer> state
    ) {
        return new EntityInvStateProvider<Integer>() {
            @Override
            public boolean inventoryFull() {
                Collection<MCHeldItem> items = heldItems.get();
                return items.stream().noneMatch(MCHeldItem::isEmpty);
            }

            @Override
            public boolean hasNonSupplyItems() {
                return JobsClean.hasNonSupplyItems(
                        heldItems.get(),
                        state.get(),
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                );
            }

            @Override
            public Map<Integer, Boolean> getSupplyItemStatus() {
                return JobsClean.getSupplyItemStatuses(
                        heldItems,
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                );
            }
        };
    }

    public MCTownState simulateDropLoot(
            MCTownState inState,
            ProductionStatus status
    ) {
        return ProductionTimeWarper.simulateDropLoot(
                inState, status, villagerIndex, MCHeldItem::Air
        );
    }

    public @Nullable MCTownState simulateCollectSupplies(
            MCTownState inState,
            int processingState
    ) {
        ImmutableMap<Integer, Predicate<MCHeldItem>> ingr = Jobs.unFn(ingredientsRequiredAtStates());
        ImmutableMap<Integer, Predicate<MCHeldItem>> tool = Jobs.unFn3(toolsRequiredAtStates);
        return ProductionTimeWarper.simulateCollectSupplies(
                inState, processingState, villagerIndex, ingr, tool, MCHeldItem::fromTown
        );
    }
}
