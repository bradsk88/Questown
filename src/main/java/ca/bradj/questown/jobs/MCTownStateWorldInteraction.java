package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends
        AbstractWorldInteraction<MCTownStateWorldInteraction.Inputs, BlockPos, MCTownItem, MCHeldItem, MCTownState> {

    public record Inputs(
            MCTownState town,
            ServerLevel level,
            UUID uuid
    ) {
    }

    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;
    private final Map<Integer, Integer> ingredientQuantityRequiredAtStates;

    public MCTownStateWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            WorkStates states,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<MCTownStateWorldInteraction.Inputs, Claim> claimSpots,
            Function<Integer, Collection<String>> specialRules
    ) {
        super(
                jobId, villagerIndex, interval, states.maxState(), Jobs.unMC(states.toolsRequired()),
                states.workRequired(), Jobs.unMCHeld(states.ingredientsRequired()),
                states.ingredientQtyRequired(), states.timeRequired(), claimSpots, specialRules
        );
        this.resultGenerator = resultGenerator;
        this.ingredientQuantityRequiredAtStates = states.ingredientQtyRequired();
    }

    @Override
    protected boolean isWorkResult(
            Inputs inputs,
            MCHeldItem item
    ) {
        // TODO: Store/get town data on town state
        return Works.isWorkResult(WorksBehaviour.TownData.noData(), item.toItem());
    }

    @Override
    protected int getWorkSpeedOf10(Inputs inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex).getEffectsAndClearExpired(
                Util.getTick(inputs.level())
        );
        return Math.max(TownVillagerMoods.compute(effects) / 10, 1);
    }

    @Override
    protected int getAffectedTime(
            Inputs inputs,
            Integer nextStepTime
    ) {
        return (int) (getTimeFactor(inputs) * nextStepTime);
    }

    private float getTimeFactor(Inputs inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex).getEffectsAndClearExpired(
                Util.getTick(inputs.level())
        );
        return WorkEffects.calculateTimeFactor(effects);
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
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(
            Inputs mcTownState
    ) {
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
    protected MCTownState setJobBlockState(
            @NotNull Inputs inputs,
            MCTownState ts,
            BlockPos position,
            AbstractWorkStatusStore.State fresh
    ) {
        return ts.setJobBlockState(position, fresh);
    }

    @Override
    protected MCTownState withEffectApplied(
            @NotNull Inputs inputs,
            MCTownState ts,
            MCHeldItem newItem
    ) {
        ItemStack s = newItem.get().toItemStack();
        ResourceLocation effect = EffectMetaItem.getEffect(s);
        return ts.withVillagerData(villagerIndex, ts.getVillager(villagerIndex).withEffect(
                new Effect(effect, EffectMetaItem.getEffectExpiry(s, Util.getTick(inputs.level)))
        ));
    }

    @Override
    protected MCTownState withKnowledge(
            @NotNull Inputs inputs,
            MCTownState ts,
            MCHeldItem newItem
    ) {
        return ts.withKnowledge(newItem);
    }

    @Override
    protected boolean isInstanze(
            MCTownItem mcTownItem,
            Class<?> clazz
    ) {
        return clazz.isInstance(mcTownItem.get().asItem());
    }

    @Override
    protected boolean isMulti(MCTownItem mcTownItem) {
        return mcTownItem.toItemStack().getCount() > 1;
    }

    @Override
    protected MCTownState getTown(Inputs inputs) {
        return inputs.town();
    }

    @Override
    protected Iterable<MCHeldItem> getResults(
            Inputs inputs,
            Collection<MCHeldItem> mcHeldItems
    ) {
        return resultGenerator.apply(inputs.level, mcHeldItems);
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

    public JobTownProvider<MCRoom, ProductionStatus> asTownJobs(
            @NotNull AbstractWorkStatusStore.State workStates,
            MCRoom mcRoom,
            BlockPos roomBlock,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        return new JobTownProvider<>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                if (workStates.processingState() == maxState) {
                    return ImmutableList.of(mcRoom);
                }
                return ImmutableList.of();
            }

            @Override
            public Map<ProductionStatus, Collection<MCRoom>> roomsToGetSuppliesForByState() {
                int curState = workStates.processingState();
                ProductionStatus curStatus = ProductionStatus.fromJobBlockStatus(curState);
                Function<MCHeldItem, Boolean> ings = ingredientsRequiredAtStates().get(curState);
                if (ings != null) {
                    return ImmutableMap.of(curStatus, ImmutableList.of(mcRoom));
                }

                Function<MCTownItem, Boolean> toolChk = toolsRequiredAtStates.get(curState);
                if (toolChk != null) {
                    return ImmutableMap.of(curStatus, ImmutableList.of(mcRoom));
                }

                if (workStates.workLeft() > 0) {
                    return ImmutableMap.of(curStatus, ImmutableList.of(mcRoom));
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
                        bp -> workStates,
                        (bp) -> true
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
                // TODO: Reduce deuplication with TownNeeds.getRoomsNeedingIngredientsOrTools
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

            @Override
            public boolean isCachingAllowed() {
                return true;
            }
        };
    }

    public EntityInvStateProvider<ProductionStatus> asInventory(
            Supplier<Collection<MCHeldItem>> heldItems,
            Supplier<Integer> state
    ) {
        return new EntityInvStateProvider<ProductionStatus>() {
            @Override
            public boolean inventoryFull() {
                Collection<MCHeldItem> items = heldItems.get();
                return items.stream().noneMatch(MCHeldItem::isEmpty);
            }

            @Override
            public boolean hasNonSupplyItems(boolean allowCaching) {
                return JobsClean.hasNonSupplyItems(
                        heldItems.get(),
                        state.get(),
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                );
            }

            @Override
            public Map<ProductionStatus, Boolean> getSupplyItemStatus() {
                return ProductionStatus.mapUnsafe(JobsClean.getSupplyItemStatuses(
                        heldItems,
                        Jobs.unFn(ingredientsRequiredAtStates()),
                        Jobs.unHeld(toolsRequiredAtStates)
                ));
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
