package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.*;
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
import ca.bradj.questown.world.MinecraftWorldAccess;
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

/**
 * NOTE: This class is for TIME WARP functionality only.
 * It is NOT used for realtime job execution logic.
 * For realtime job execution, see {@link RealtimeWorldInteraction}, {@link TickTownProvider} and {@link DeclarativeJobTicker}.
 */
public class TimeWarpWorldInteraction extends
        AbstractWorldInteraction<TimeWarpWorldInteraction.Inputs, BlockPos, MCTownItem, MCHeldItem, MCTownState> {

    private final BlockPos townPos;
    private final Collection<BlockPos> roomPositions;
    private final @Nullable BlockPos assignedWorkBlock;
    private final Map<ProductionStatus, Collection<String>> specialRulesMap;
    private final @Nullable ca.bradj.questown.world.QTWorldAccess warpWorld;

    public record Inputs(MCTownState town, ServerLevel level, UUID vUUID) {
    }

    private final BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator;

    public TimeWarpWorldInteraction(
            BlockPos townPos,
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            DeclarativeJobChecks<Inputs, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Function<TimeWarpWorldInteraction.Inputs, Claim> claimSpots,
            Map<ProductionStatus, Collection<String>> specialRules,
            Collection<BlockPos> roomPositions,
            @Nullable BlockPos assignedWorkBlock,
            @Nullable ca.bradj.questown.world.QTWorldAccess warpWorld
    ) {
        super(jobId, villagerIndex, interval, maxState, checks, claimSpots, specialRules);
        this.resultGenerator = resultGenerator;
        this.townPos = townPos;
        this.roomPositions = roomPositions;
        this.assignedWorkBlock = assignedWorkBlock;
        this.specialRulesMap = specialRules;
        this.warpWorld = warpWorld;
    }

    private ca.bradj.questown.world.QTWorldAccess resolveWorld(Inputs inputs) {
        if (warpWorld != null) {
            return warpWorld;
        }
        return MinecraftWorldAccess.silent(inputs.level());
    }

    public boolean shouldUseRealWorkBlock() {
        if (assignedWorkBlock == null) {
            return false;
        }
        return specialRulesMap.values().stream()
                .flatMap(Collection::stream)
                .anyMatch(rule -> rule.contains("slot"));
    }

    public @Nullable BlockPos getAssignedWorkBlock() {
        return assignedWorkBlock;
    }

    public @Nullable ca.bradj.questown.world.QTWorldAccess getWarpWorld() {
        return warpWorld;
    }

    @Override
    protected int getWorkSpeedOf10(Inputs inputs) {
        TownState.VillagerData<MCHeldItem> villager = inputs.town().getVillager(villagerIndex);
        Collection<Effect> effects = villager.getEffectsAndClearExpired(Util.getTick(inputs.level()));
        int base = TownVillagerMoods.compute(effects) / 10;
        float mult = RealtimeWorldInteraction.proficiencyMultiplier(
                ServerJobsRegistry.getProficiencyId(getJobId()),
                villager::getProficiencyLevel
        );
        return Math.max(Math.round(base * mult), 1);
    }

    @Override
    protected int getAffectedTime(
            Inputs inputs,
            Integer nextStepTime
    ) {
        return (int) (getTimeFactor(inputs) * nextStepTime);
    }

    @Override
    protected MCTownState onWorkActionCompleted(
            Inputs inputs,
            MCTownState town
    ) {
        String profId = ServerJobsRegistry.getProficiencyId(getJobId());
        if (profId == null || town == null) {
            return town;
        }
        TownState.VillagerData<MCHeldItem> villager = town.getVillager(villagerIndex);
        Map<String, Float> leveled = ca.bradj.questown.jobs.Proficiency.levelUp(
                villager.getProficiencies(),
                profId,
                ca.bradj.questown.core.Config.PROFICIENCY_GAIN_PER_TICK.get().floatValue(),
                ca.bradj.questown.core.Config.PROFICIENCY_DECAY_PER_TICK.get().floatValue(),
                interval
        );
        return town.withVillagerData(villagerIndex, villager.withProficiencies(leveled));
    }

    private float getTimeFactor(Inputs inputs) {
        Collection<Effect> effects = inputs.town().getVillager(villagerIndex)
                                           .getEffectsAndClearExpired(Util.getTick(inputs.level()));
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
            if (!itemStack.isDamageableItem()) {
                return tuwn;
            }
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
    protected WorkOutput<MCTownState, WorkPosition<BlockPos>> getWithSurfaceInteractionPos(
            Inputs inputs,
            WorkOutput<MCTownState, WorkPosition<BlockPos>> v
    ) {
        return Util.workWithSurfaceInteractionPos(inputs.level(), v);
    }

    @Override
    protected ArrayList<WorkPosition<BlockPos>> shuffle(
            Inputs inputs,
            Collection<WorkPosition<BlockPos>> workSpots
    ) {
        return new ArrayList<>(Compat.shuffle(ImmutableList.copyOf(workSpots), inputs.level));
    }

    @Override
    protected WorkedSpot<BlockPos> getCurWorkedSpot(
            Inputs inputs,
            MCTownState stateSource,
            BlockPos workSpot
    ) {
        int stateAfterWork = stateSource.getJobBlockState(workSpot).processingState();
        return new WorkedSpot<>(workSpot, stateAfterWork);
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(
            Inputs mcTownState,
            int villagerIndex
    ) {
        return ProductionTimeWarper.getHeldItems(mcTownState.town(), villagerIndex);
    }

    @Override
    protected void triggerCompletionAdvancement(
            Inputs inputs,
            BlockPos position
    ) {
        // Only trigger for realtime
    }

    @Override
    protected void preStateChangeHooks(
            @NotNull MCTownState ctx,
            Collection<String> rules,
            Inputs inputs,
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
            Inputs inputs,
            WorkedSpot<BlockPos> position,
            MCHeldItem item
    ) {
        MCTownState afterHook = PostInsertHook.run(
                mcTownState,
                rules,
                resolveWorld(inputs),
                position,
                item.get().toMCItemStack(),
                ts -> ts.withBOPCleared(inputs.vUUID),
                inputs.vUUID
        );
        // PostInsertHook.run() returns null if no rules were applied
        if (afterHook == null) {
            afterHook = mcTownState;
        }
        // Track the inserted item for potential recovery if NO_SUPPLIES is encountered later
        return afterHook.withInsertedItem(villagerIndex, position.workPosition(), item);
    }

    @Override
    protected BlockPos getTownPos(Inputs inputs) {
        return townPos;
    }

    @Override
    protected @Nullable MCTownState preExtractHook(
            MCTownState town,
            Collection<String> rules,
            Inputs inputs,
            BlockPos position
    ) {
        Item insertedItem = null; // TODO: Support inserted item history?
        return PreExtractHook.run(
                town, rules, resolveWorld(inputs), (ctx, i, s) -> {
                    Inputs in = new Inputs(ctx, inputs.level(), inputs.vUUID());
                    return tryGiveItems(in, ImmutableList.of(i), position);
                }, position, insertedItem, () -> {
                },
                () -> roomPositions
        );
    }

    @Override
    protected MCTownState postExtractHook(
            MCTownState mcTownState,
            Collection<String> rules,
            Inputs inputs,
            BlockPos position,
            @Nullable MCHeldItem extractedItem
    ) {
        // Post-extract rules apply item-data / hunger / mood / knowledge — none touch the
        // deferred block state the WarpWorldAccess holds, but non-migrated rules (SCOUT_LOOT)
        // DO need a real ServerLevel for loot-table RNG via asServerLevel(). The realtime path
        // passes a real-level world here too; resolveWorld() would hand back the WarpWorldAccess
        // whose asServerLevel() is null, so use the real level directly.
        ca.bradj.questown.world.QTWorldAccess postExtractWorld = inputs.level != null
                ? MinecraftWorldAccess.silent(inputs.level)
                : resolveWorld(inputs);
        return PostExtractHook.run(
                mcTownState, townPos, rules, postExtractWorld, position, (ctx, itemData) -> {
                    if (extractedItem == null || extractedItem.isEmpty()) {
                        return ctx;
                    }
                    CompoundTag t = extractedItem.get().toMCItemStack().getOrCreateTag();
                    itemData.forEach(t::putInt);
                    return ctx;
                }, (in, up) -> in,
                (in, effect, durationTicks) -> {
                    long expiry = Util.getTick(inputs.level) + durationTicks;
                    return in.withVillagerData(
                            villagerIndex,
                            in.getVillager(villagerIndex).withEffect(new Effect(effect, expiry))
                    );
                },
                extractedItem,
                (in, foundLoot) -> in.withKnowledge(foundLoot)
        );
    }

    @Override
    protected MCTownState setJobBlockState(
            @NotNull Inputs inputs,
            MCTownState ts,
            BlockPos position,
            State fresh
    ) {
        return ts.setJobBlockState(position, fresh);
    }

    @Override
    protected boolean isMulti(MCTownItem mcTownItem) {
        return mcTownItem.toQTItemStack().getCount() > 1;
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
        Iterable<MCHeldItem> base = resultGenerator.apply(inputs.level, mcHeldItems);
        return ca.bradj.questown.mobs.helperchicken.ChickenArcLootGuarantee.maybePrepend(
                resolveFlagAtTownPos(inputs), base
        );
    }

    private ca.bradj.questown.town.entity.TownFlagBlockEntity resolveFlagAtTownPos(Inputs inputs) {
        if (inputs.level == null) {
            return null;
        }
        if (inputs.level.getBlockEntity(this.townPos)
                instanceof ca.bradj.questown.town.entity.TownFlagBlockEntity flag) {
            return flag;
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
    public boolean tryGrabbingInsertedSupplies(Inputs mcExtra) {
        // TODO[Warp]: Implement
        return true;
    }

    @Override
    public int timesInserted(Inputs inputs) {
        // TODO[Warp]: Implement
        return 0;
    }

    @Override
    protected void registerUnmetNeed(
            Inputs inputs,
            NeedsRegistrations.Need ingredientIndex
    ) {
        // TODO[WARP]: Implement tracking of needs
    }

    @Override
    protected void registerUnmetRoom(Inputs inputs) {
        // TODO[WARP]: Implement tracking of needs
    }

    /**
     * Injects ticks to simulate time passing during warp.
     * Ensures ticksSinceLastAction is at least equal to the work interval
     * so that work can happen on each warp tick.
     */
    public void injectTicks(int ticks) {
        ticksSinceLastAction += ticks;
        // Ensure we always have enough ticks to pass the interval check during warp
        // This is necessary because each warp tick creates a new TimeWarpWorldInteraction
        // with ticksSinceLastAction=0, and consecutive ticks have ticksSincePrevious=1
        ticksSinceLastAction = Math.max(ticksSinceLastAction, interval);
    }

    public JobTownProvider<MCRoom> asTownJobs(
            @NotNull State workStates,
            RoomRecipeMatch<MCRoom> mcRoom,
            BlockPos roomBlock,
            @NotNull ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers,
            Supplier<Signals.DayTime> dayTime
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
            public Signals.DayTime getDayTime() {
                return dayTime.get();
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
                // For warp, we check if the fake room block is at each state
                // and return a dependency that evaluates to true for the current state
                ImmutableMap.Builder<Integer, LZCD.Dependency<Void>> b = ImmutableMap.builder();
                for (int state = 0; state <= maxState; state++) {
                    final int s = state;
                    b.put(state, new SimpleDependency("warp room has workable block at state " + state) {
                        @Override
                        protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
                            boolean atState = workStates.processingState() == s;
                            String reason = atState
                                    ? "work block at state " + s
                                    : "work block not at state " + s + " (actual: " + workStates.processingState() + ")";
                            return new Populated<>(
                                    "warp workable blocks at state " + s,
                                    WithReason.always(atState, reason),
                                    ImmutableMap.of(),
                                    null
                            ) {
                                @Override
                                protected String stringRep() {
                                    return "WarpWorkableBlocks[" + s + "]=" + atState;
                                }
                            };
                        }

                        @Override
                        public String describe() {
                            return "warp room workable at state " + s;
                        }
                    });
                }
                return b.build();
            }

            @Override
            public LZCD.Dependency<Void> hasSuppliesV2() {
                return new SimpleDependency("town has supplies for warp") {
                    @Override
                    protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
                        int curState = workStates.processingState();
                        boolean hasSupplies = false;
                        String reason = "no supplies found";

                        PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
                        if (ings != null) {
                            for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                                if (container.hasItem(i -> ings.test(MCHeldItem.fromTown(i)))) {
                                    hasSupplies = true;
                                    reason = "container has ingredient for state " + curState;
                                    break;
                                }
                            }
                        }
                        if (!hasSupplies) {
                            PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
                            if (toolChk != null) {
                                for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
                                    if (container.hasItem(toolChk::test)) {
                                        hasSupplies = true;
                                        reason = "container has tool for state " + curState;
                                        break;
                                    }
                                }
                            }
                        }

                        final boolean result = hasSupplies;
                        final String finalReason = reason;
                        return new Populated<>(
                                "warp supplies check",
                                WithReason.always(result, finalReason),
                                ImmutableMap.of(),
                                null
                        ) {
                            @Override
                            protected String stringRep() {
                                return "WarpSupplies [" + result + ": " + finalReason + "]";
                            }
                        };
                    }

                    @Override
                    public String describe() {
                        return "hasSuppliesV2 for warp";
                    }
                };
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

            // TODO[ASAP]: Confirm that hasSupplies is actually not used on 1.19.2 branch
//            @Override
//            public boolean hasSupplies() {
//                // TODO: Reduce deuplication with DeclarativeJob.roomsNeedingIngredientsOrTools
//                int curState = workStates.processingState();
//                PredicateCollection<MCHeldItem, ?> ings = checks.getIngredientsForStep(curState);
//                if (ings != null) {
//                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
//                        if (container.hasItem(i -> ings.test(MCHeldItem.fromTown(i)))) {
//                            return true;
//                        }
//                    }
//                }
//                PredicateCollection<MCTownItem, ?> toolChk = checks.getToolsForStep(curState);
//                if (toolChk != null) {
//                    for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
//                        if (container.hasItem(toolChk::test)) {
//                            return true;
//                        }
//                    }
//                }
//                return false;
//            }

            @Override
            public boolean hasSpace() {
                return containers.stream().anyMatch(v -> !v.isFull());
            }
        };
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
                return DeclarativeJobTicker.getSupplyItemStatuses(
                        heldItems, checks.getAllRequiredIngredients(), (s) -> true, // TODO[WARP]: Implement this?
                        Jobs.unTown(checks.getAllRequiredTools()), (s) -> true, checks.getAllRequiredWork(), maxState
                );
            }
        };
    }

    public MCTownState simulateDropLoot(
            MCTownState inState,
            ProductionStatus status
    ) {
        return ProductionTimeWarper.simulateDropLoot(inState, status, villagerIndex, MCHeldItem::Air);
    }

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

    /**
     * Recovers items that were inserted into a work block during warp.
     * Called when NO_SUPPLIES is encountered, meaning the villager ran out of supplies mid-cycle.
     * Items are deposited back to containers, or given to the villager if containers are full.
     *
     * @return Updated town state with items recovered and tracking cleared
     */
    public MCTownState recoverInsertedItems(MCTownState town) {
        java.util.Map.Entry<MCTownState, ImmutableList<MCHeldItem>> result = town.withInsertedItemsCleared(villagerIndex);
        MCTownState newState = result.getKey();
        ImmutableList<MCHeldItem> recoveredItems = result.getValue();

        if (recoveredItems.isEmpty()) {
            return newState;
        }

        // Deposit recovered items back to containers
        ImmutableList<MCHeldItem> notDeposited = newState.depositItems(recoveredItems);
        if (!notDeposited.isEmpty()) {
            // If containers are full, try to give items to villager
            for (MCHeldItem item : notDeposited) {
                TownState.VillagerData<MCHeldItem> vd = newState.getVillager(villagerIndex).withAddedItem(item);
                if (vd != null) {
                    newState = newState.withVillagerData(villagerIndex, vd);
                }
                // If villager inventory is also full, items are lost (edge case)
            }
        }
        return newState;
    }

    /**
     * Simulates recovering items that were inserted during warp.
     * Used by the NO_SUPPLIES handler to recover items and reset state.
     *
     * @return Updated town state, or null if nothing to recover
     */
    public @Nullable MCTownState simulateRecoverInsertedItems(MCTownState inState) {
        java.util.Map.Entry<MCTownState, ImmutableList<MCHeldItem>> result = inState.withInsertedItemsCleared(villagerIndex);
        ImmutableList<MCHeldItem> recoveredItems = result.getValue();
        if (recoveredItems.isEmpty()) {
            return null; // Nothing to recover
        }
        return recoverInsertedItems(inState);
    }
}
