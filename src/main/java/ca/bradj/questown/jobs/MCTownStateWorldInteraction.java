package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.Effect;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.TownVillagerMoods;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends
        AbstractStateInteraction<Inpoots<MCTownState, ServerLevel>, BlockPos, MCTownItem, MCHeldItem, MCTownState> {

    private final BlockPos townPos;

    public record Inputs(MCTownState town, ServerLevel level, UUID vUUID) {
    }

    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;

    public MCTownStateWorldInteraction(
            BlockPos townPos,
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            DeclarativeJobChecks<Inpoots<MCTownState, ServerLevel>, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<Inpoots<MCTownState, ServerLevel>, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules
    ) {
        super(jobId, villagerIndex, interval, maxState, checks, claimSpots, specialRules);
        this.resultGenerator = resultGenerator;
        this.townPos = townPos;
    }

    @Override
    protected int getWorkSpeedOf10(Inpoots<MCTownState, ServerLevel> inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex)
                                           .getEffectsAndClearExpired(Util.getTick(inputs.level()));
        return Math.max(TownVillagerMoods.compute(effects) / 10, 1);
    }

    @Override
    protected int getAffectedTime(
            Inpoots<MCTownState, ServerLevel> inputs,
            Integer nextStepTime
    ) {
        return (int) (getTimeFactor(inputs) * nextStepTime);
    }

    private float getTimeFactor(Inpoots<MCTownState, ServerLevel> inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex)
                                           .getEffectsAndClearExpired(Util.getTick(inputs.level()));
        return WorkEffects.calculateTimeFactor(effects);
    }

    /**
     * Returns the minimum timer value from the job definition.
     * Used by warp to ensure step intervals are small enough to catch timer completions.
     *
     * @return Minimum timer value, or null if no timers are defined
     */
    public @Nullable Integer getMinTimerValue() {
        Map<Integer, Integer> timeReqs = checks.getAllRequiredTime();
        if (timeReqs.isEmpty()) {
            return null;
        }
        return timeReqs.values().stream()
                .filter(v -> v != null && v > 0)
                .min(Integer::compareTo)
                .orElse(null);
    }

    @Override
    protected MCTownState setHeldItem(
            Inpoots<MCTownState, ServerLevel> uxtra,
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
            Inpoots<MCTownState, ServerLevel> mcTownState,
            @Nullable MCTownState tuwn,
            PredicateCollection<MCTownItem, ?> isExpectedTool
    ) {
        if (tuwn == null) {
            tuwn = mcTownState.town();
        }

        int i = -1;
        for (MCHeldItem item : this.getHeldItems(mcTownState, villagerIndex)) {
            i++;
            if (!isExpectedTool.test(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toQTItemStack();
            itemStack.hurt(1, mcTownState.level().getRandom(), null);
            if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
                itemStack = ItemStack.EMPTY;
            }
            return setHeldItem(mcTownState, tuwn, villagerIndex, i, MCHeldItem.fromMCItemStack(itemStack));
        }
        return tuwn;
    }

    @Override
    protected boolean isWorkSpotReadyForItem(
            Inpoots<MCTownState, ServerLevel> mcTownState,
            MCHeldItem item,
            BlockPos bp
    ) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(
            Inpoots<MCTownState, ServerLevel> mcTownState
    ) {
        return mcTownState.town();
    }

    @Override
    protected WorkOutput<MCTownState, WorkPosition<BlockPos>> getWithSurfaceInteractionPos(
            Inpoots<MCTownState, ServerLevel> inputs,
            WorkOutput<MCTownState, WorkPosition<BlockPos>> v
    ) {
        return Util.workWithSurfaceInteractionPos(inputs.level(), v);
    }

    @Override
    protected ArrayList<WorkPosition<BlockPos>> makeMutableShuffledCopy(
            Inpoots<MCTownState, ServerLevel> inputs,
            Collection<WorkPosition<BlockPos>> workSpots
    ) {
        return new ArrayList<>(Compat.shuffle(ImmutableList.copyOf(workSpots), inputs.level()));
    }

    @Override
    protected WorkedSpot<BlockPos> getWorkedSpotWithUpToDateState(
            Inpoots<MCTownState, ServerLevel> inputs,
            MCTownState stateSource,
            BlockPos workSpot
    ) {
        int stateAfterWork = stateSource.getJobBlockState(workSpot).processingState();
        return new WorkedSpot<>(workSpot, stateAfterWork);
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            Inpoots<MCTownState, ServerLevel> mcTownState,
            int villagerIndex
    ) {
        return ProductionTimeWarper.getHeldItems(mcTownState.town(), villagerIndex);
    }

    @Override
    protected void triggerCompletionAdvancement(
            Inpoots<MCTownState, ServerLevel> inputs,
            BlockPos position
    ) {
        // Only trigger for realtime
    }

    @Override
    protected void preStateChangeHooks(
            @NotNull MCTownState ctx,
            Collection<String> rules,
            Inpoots<MCTownState, ServerLevel> inputs,
            WorkSpot<Integer, BlockPos> position
    ) {
        PreStateChangeHook.run(
                rules, (pose) -> {
                }, (job) -> {
                    // TODO[Warp]: Set Job
                }
        );
    }

    @Override
    protected @NotNull MCTownState postInsertHook(
            @NotNull MCTownState mcTownState,
            Collection<String> rules,
            Inpoots<MCTownState, ServerLevel> inputs,
            WorkedSpot<BlockPos> position,
            MCHeldItem item
    ) {
        return PostInsertHook.run(
                mcTownState,
                rules,
                inputs.level(),
                position,
                item.get().toMCItemStack(),
                ts -> ts.withBOPCleared(inputs.villagerUUID()),
                inputs.villagerUUID()
        );
    }

    @Override
    protected BlockPos getTownPos(Inpoots<MCTownState, ServerLevel> inputs) {
        return townPos;
    }

    @Override
    protected @Nullable MCTownState preExtractHook(
            MCTownState town,
            Collection<String> rules,
            Inpoots<MCTownState, ServerLevel> inputs,
            BlockPos position
    ) {
        Item insertedItem = null; // TODO: Support inserted item history?
        return PreExtractHook.run(
                town, rules, inputs.level(), (ctx, i, s) -> {
                    Inpoots<MCTownState, ServerLevel> in = new Inpoots<>(ctx, inputs.level(), inputs.villagerUUID());
                    return tryGiveItems(in, ImmutableList.of(i), position);
                }, position, insertedItem, () -> {}
        );
    }

    @Override
    protected MCTownState postExtractHook(
            MCTownState mcTownState,
            Collection<String> rules,
            Inpoots<MCTownState, ServerLevel> inputs,
            BlockPos position,
            MCHeldItem extractedItem
    ) {
        return PostExtractHook.run(
                mcTownState,
                townPos,
                rules,
                inputs.level(),
                position,
                (ctx, itemData) -> {
                    CompoundTag t = extractedItem.get().toMCItemStack().getOrCreateTag();
                    itemData.forEach(t::putInt);
                    return ctx;
                },
                (in, up) -> in
        );
    }

    @Override
    protected MCTownState setJobBlockState(
            @NotNull Inpoots<MCTownState, ServerLevel> inputs,
            MCTownState ts,
            BlockPos position,
            State fresh
    ) {
        return ts.setJobBlockState(position, fresh);
    }

    @Override
    protected MCTownState withEffectApplied(
            @NotNull Inpoots<MCTownState, ServerLevel> inputs,
            MCTownState ts,
            MCHeldItem newItem
    ) {
        ItemStack s = newItem.get().toQTItemStack();
        ResourceLocation effect = EffectMetaItem.getEffect(s);
        return ts.withVillagerData(
                villagerIndex,
                ts.getVillager(villagerIndex)
                  .withEffect(new Effect(effect, EffectMetaItem.getEffectExpiry(s, Util.getTick(inputs.level()))))
        );
    }

    @Override
    protected MCTownState withKnowledge(
            @NotNull Inpoots<MCTownState, ServerLevel> inputs,
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
    protected boolean isStacked(MCTownItem mcTownItem) {
        return mcTownItem.toQTItemStack().getCount() > 1;
    }

    @Override
    protected MCTownState getTown(Inpoots<MCTownState, ServerLevel> inputs) {
        return inputs.town();
    }

    @Override
    protected ImmutableList<MCHeldItem> getResults(
            Inpoots<MCTownState, ServerLevel> inputs,
            Collection<MCHeldItem> mcHeldItems
    ) {
        return ImmutableList.copyOf(resultGenerator.apply(inputs.level(), mcHeldItems));
    }

    @Override
    protected boolean isEntityClose(
            Inpoots<MCTownState, ServerLevel> mcTownState,
            BlockPos position
    ) {
        return true;
    }

    @Override
    protected boolean isServerUpAndTownDataReadable(Inpoots<MCTownState, ServerLevel> mcTownState) {
        return true;
    }

    @Override
    public boolean tryGrabbingInsertedSupplies(Inpoots<MCTownState, ServerLevel> mcExtra) {
        // TODO[Warp]: Implement
        return true;
    }

    @Override
    public int timesInserted(Inpoots<MCTownState, ServerLevel> inputs) {
        // TODO[Warp]: Implement
        return 0;
    }

    @Override
    protected void registerUnmetNeed(
            Inpoots<MCTownState, ServerLevel> inputs,
            NeedsRegistrations.Need ingredientIndex
    ) {
        // TODO[WARP]: Implement tracking of needs
    }

    @Override
    protected void registerUnmetRoom(Inpoots<MCTownState, ServerLevel> inputs) {
        // TODO[WARP]: Implement tracking of needs
    }

    public void injectTicks(int interval) {
        ticksSinceLastAction += interval;
    }

    public JobTownProvider<MCRoom> asTownJobs(
            @NotNull State workStates,
            RoomRecipeMatch<MCRoom> mcRoom,
            BlockPos roomBlock,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        return new JobTownProvider<MCRoom>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                if (workStates.processingState() == maxState) {
                    return ImmutableList.of(mcRoom.room);
                }
                return ImmutableList.of();
            }

            @Override
            public Collection<MCRoom> roomsAtState(Integer state) {
                if (workStates.processingState() == state) {
                    return ImmutableList.of(mcRoom.room);
                }
                return ImmutableList.of();
            }

            @Override
            public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
                int curState = workStates.processingState();
                PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
                if (ings != null) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
                if (toolChk != null) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                if (workStates.workLeft() > 0) {
                    return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                            curState,
                            ImmutableList.of(new NVIRoom<>(mcRoom, false))
                    ));
                }

                return new RoomsNeedingVillagerInput<>(ImmutableMap.of());
            }

            @Override
            public Map<Integer, LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
                // For warp: return a map indicating which states have workable blocks
                // The current processing state is workable if we're at that state
                ImmutableMap.Builder<Integer, LZCD.Dependency<Void>> b = ImmutableMap.builder();
                for (int state = 0; state < maxState; state++) {
                    final int s = state;
                    b.put(state, new ConstantDep(
                            "room has workable blocks at state " + state + " [warp]",
                            workStates.processingState() == s
                    ));
                }
                return b.build();
            }

            @Override
            public LZCD.Dependency<Void> hasSuppliesV2() {
                int curState = workStates.processingState();
                if (curState >= maxState) {
                    return new ConstantDep("work complete, no supplies needed [warp]", false);
                }
                // Check if current state actually requires ingredients or tools
                boolean needsIngredients = checks.getIngredientsForStep(curState) != null;
                boolean needsTools = checks.getToolsForStep(curState) != null;
                if (!needsIngredients && !needsTools) {
                    return new ConstantDep("state " + curState + " needs no supplies [warp]", false);
                }
                // State requires supplies - delegate to hasSupplies() for container check
                return new ConstantDep("has supplies [warp]", hasSupplies());
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return false;
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> ImmutableList.of(() -> ImmutableList.of(
                                roomBlock)), bp -> workStates, (bp) -> true
                );
                ImmutableList.Builder<Integer> b = ImmutableList.builder();
                statesWithUnfinishedWork.forEach(state -> {
                    if (checks.getToolsForStep(state) == null) {
                        b.add(state);
                    }
                });
                return b.build();
            }

            @Override
            public boolean hasSupplies() {
                // TODO: Reduce deuplication with DeclarativeJob.roomsNeedingIngredientsOrTools
                int curState = workStates.processingState();
                PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
                if (ings != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(i -> ings.test(MCHeldItem.fromTown(i)))) {
                            return true;
                        }
                    }
                }
                PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
                if (toolChk != null) {
                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                        if (container.hasItem(toolChk::test)) {
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
        return new EntityInvStateProvider<>() {
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
                        checks.getAllRequiredIngredients(),
                        Jobs.unTown(checks.getAllRequiredTools())
                );
            }

            @Override
            public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return JobsClean.getSupplyItemStatuses(
                        heldItems, checks.getAllRequiredIngredients(), (s) -> true, // TODO[WARP]: Implement this?
                        Jobs.unTown(checks.getAllRequiredTools()), (s) -> true, checks.getAllRequiredWork(), maxState
                );
            }
        };
    }

    @Override
    public MCTownState simulateDropLoot(
            MCTownState inState,
            ProductionStatus status
    ) {
        return ProductionTimeWarper.simulateDropLoot(inState, status, villagerIndex, MCHeldItem::Air);
    }

    @Override
    public @Nullable MCTownState simulateCollectSupplies(
            MCTownState inState,
            int processingState
    ) {
        Map<Integer, ? extends Predicate<MCHeldItem>> ingr = checks.getAllRequiredIngredients();
        Map<Integer, ? extends Predicate<MCHeldItem>> tool = Jobs.unTown(checks.getAllRequiredTools());
        return ProductionTimeWarper.simulateCollectSupplies(
                inState,
                processingState,
                villagerIndex,
                ingr,
                tool,
                MCHeldItem::fromTown
        );
    }

    @Override
    protected void iterate(
            Iterable<MCHeldItem> newItemsSource,
            Function<MCHeldItem, MCHeldItem> push
    ) {
        Util.iterate(newItemsSource, push::apply);
    }
}
