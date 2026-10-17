package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.*;
import java.util.stream.Stream;

public class DeclarativeJobs {
    public static final IProductionStatusFactory<ProductionStatus> STATUS_FACTORY = new IProductionStatusFactory<>() {
        @Override
        public ProductionStatus fromJobBlockState(int s) {
            return ProductionStatus.fromJobBlockStatus(s);
        }

        @Override
        public ProductionStatus waitingForTimedState() {
            return ProductionStatus.FACTORY.waitingForTimedState();
        }

        @Override
        public ProductionStatus noWorkPossible() {
            return ProductionStatus.FACTORY.noWorkPossible();
        }

        @Override
        public ProductionStatus droppingLoot() {
            return ProductionStatus.FACTORY.droppingLoot();
        }

        @Override
        public ProductionStatus noSpace() {
            return ProductionStatus.FACTORY.noSpace();
        }

        @Override
        public ProductionStatus goingToJobSite() {
            return ProductionStatus.FACTORY.goingToJobSite();
        }

        @Override
        public ProductionStatus noJobSite() {
            return ProductionStatus.FACTORY.noJobSite();
        }

        @Override
        public ProductionStatus noSupplies() {
            return ProductionStatus.FACTORY.noSupplies();
        }

        @Override
        public ProductionStatus collectingSupplies() {
            return ProductionStatus.FACTORY.collectingSupplies();
        }

        @Override
        public ProductionStatus idle() {
            return ProductionStatus.FACTORY.idle();
        }

        @Override
        public ProductionStatus extractingProduct() {
            return ProductionStatus.FACTORY.extractingProduct();
        }

        @Override
        public ProductionStatus relaxing() {
            return ProductionStatus.FACTORY.relaxing();
        }
    };

    public static <INGREDIENT, ITEM extends Item<ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, ITEM>> Map<Integer, Boolean> getSupplyItemStatus(
            Collection<HELD_ITEM> journalItems,
            ImmutableMap<Integer, INGREDIENT> ingredientsRequiredAtStates,
            ImmutableMap<Integer, INGREDIENT> toolsRequiredAtStates,
            BiPredicate<INGREDIENT, HELD_ITEM> matchFn
    ) {
        // TODO: Compare with JobsClean version and eliminate one
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, INGREDIENT> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journalItems.stream().anyMatch(v -> matchFn.test(ingr, v));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
    }

    private static ImmutableMap<ProductionStatus, Function<HandlerInputs, MCTownState>> handler;

    public static BiFunction<Integer, SignalSource, ProductionJournal<MCTownItem, MCHeldItem>> journalInitializer(JobID jobId) {
        return (capacity, signalSource) -> new ProductionJournal<>(
                jobId,
                signalSource,
                capacity,
                MCHeldItem::Air,
                STATUS_FACTORY
        );
    }

    // TODO: Unit test. This calculation is fairly sensitive and critical.
    public static ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<BlockPos>> rooms(
            @NotNull Integer maxState,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomHandle,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            Predicate<BlockPos> isJobBlock
    ) {
        Stream<RoomsNeedingVillagerInput.NVIRoom<MCRoom, ResourceLocation, BlockPos>> rooms = roomHandle.getMatches()
                                                                                                        .stream();
        //TODO: Validate that this is actually needed
        rooms = rooms.filter(v -> !v.dueToWorkOnly());

        return DeclarativeJobs.<BlockPos, MCRoom, ResourceLocation>rooms(
                () ->
                        roomHandle.getMatches().stream()
                                  .filter(v -> !v.dueToWorkOnly()) //TODO: Validate that this is actually needed
                                  .collect(ImmutableList.toImmutableList()),
                work::getJobBlockState,
                isJobBlock,
                BlockPos::toShortString,
                maxState
        );

//        ImmutableMap.Builder<Integer, RoomsWithWorkableStatefulBlocks> b = ImmutableMap.builder();
//        Supplier<Rooms> e = () -> {
//            ImmutableMap.Builder<BlockPos, Integer> spotStatuses = ImmutableMap.builder();
//            ImmutableMap.Builder<BlockPos, Boolean> spotJBs = ImmutableMap.builder();
//            Map<MCRoom, List<Integer>> roomStatuses = new HashMap<>();
//            Stream<NVIRoom<MCRoom, ResourceLocation, BlockPos>> rooms = roomHandle.getMatches().stream();
//
//            //TODO: Validate that this is actually needed
//            rooms = rooms.filter(v -> !v.dueToWorkOnly());
//            return JobsClean.rooms(
//
//                    rooms.forEach(match -> {
//                        for (Map.Entry<BlockPos, ?> entry : match.room().getContainedBlocks().entrySet()) {
//                            BlockPos bp = entry.getKey();
//                            State jobBlockState = work.getJobBlockState(bp);
//                            if (jobBlockState == null) {
//                                continue;
//        );
    }

    public static SupplyChecks<MCHeldItem> toSupplyChecks(DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks) {
        return new SupplyChecks<>() {
            @Override
            public Map<Integer, ? extends Predicate<MCHeldItem>> getIngredientsForStep() {
                return checks.getAllRequiredIngredients();
            }

            @Override
            public Boolean isIngredientRequiredAtStep(Integer integer) {
                return checks.isIngredientRequiredAtStep(integer);
            }

            @Override
            public Map<Integer, ? extends Predicate<MCHeldItem>> getToolsForStep() {
                return Jobs.unTown(checks.getAllRequiredTools());
            }

            @Override
            public Boolean isToolRequiredAtStep(Integer integer) {
                return checks.isToolRequiredAtStep(integer);
            }

            @Override
            public Map<Integer, Integer> getWorkRequiredAtStep() {
                return checks.getAllRequiredWork();
            }
        };
    }

//    private static LZCD.Dependency<Void> supplies(
//            ServerLevel level,
//            Supplier<? extends Map<Integer, ? extends LZCD.Dependency<Void>>> roomsHaveWorkableBlocks,
//            TownInterface rooms,
//            Map<Integer, PredicateCollection<MCHeldItem, MCHeldItem>> ingredients,
//            Map<Integer, PredicateCollection<MCTownItem, MCTownItem>> tools,
//            Predicate<RoomRecipeMatch<MCRoom>> shouldGetSuppliesFromRoom,
//            Predicate<BlockPos> isJobBlock,
//            Predicate<ResourceLocation> isJobSite
//    ) {
//        return new TownHasSupplies<MCHeldItem, MCTownItem>(
//                ingredients::get,
//                tools::get,
//                () -> rooms.getRoomHandle().getMatches(shouldGetSuppliesFromRoom).stream()
//                           .map(v -> new ContainersClean.JobSite<MCContainer>() {
//                               @Override
//                               public ImmutableList<ContainersClean.Block<MCContainer>> getBlocks() {
//                                   return null;
//                               }
//
//                               @Override
//                               public boolean isJobSite() {
//                                   return false;
//                               }
//                           })
//                           .collect(ImmutableList.toImmutableList())
//        );
//    }

    private record HandlerInputs(TimeWarpWorldInteraction wi, TimeWarpWorldInteraction.Inputs inState,
                                 ProductionStatus status, State workBlockState, Integer maxState, BlockPos workPos) {
    }

    public static void staticInitialize() {
        ImmutableMap.Builder<ProductionStatus, Function<HandlerInputs, @Nullable MCTownState>> b = ImmutableMap.builder();
        Function<HandlerInputs, @Nullable MCTownState> tryWorking = ii -> {
            @Nullable WorkOutput<MCTownState, WorkPosition<BlockPos>> v = ii.wi.tryWorking(
                    ii.inState,
                    new WorkPosition<>(ii.workPos, ii.workPos)
            );
            if (v == null) {
                return null;
            }
            return v.town();
        };

        for (int i = 0; i < ProductionStatus.firstNonCustomIndex; i++) {
            b.put(
                    ProductionStatus.fromJobBlockStatus(i), (
                            HandlerInputs ii
                    ) -> {
                        if (!ii.status.isWorkingOnProduction()) {
                            return ii.inState.town();
                        }
                        return tryWorking.apply(ii);
                    }
            );
        }
        b.put(ProductionStatus.EXTRACTING_PRODUCT, tryWorking);
        b.put(ProductionStatus.DROPPING_LOOT, i -> i.wi.simulateDropLoot(i.inState.town(), i.status));
        b.put(
                ProductionStatus.COLLECTING_SUPPLIES,
                i -> i.wi.simulateCollectSupplies(i.inState.town(), i.workBlockState.processingState())
        );
        b.put(ProductionStatus.RELAXING, i -> null);
        b.put(ProductionStatus.WAITING_FOR_TIMED_STATE, i -> null);
        b.put(ProductionStatus.NO_SPACE, i -> null);
        b.put(ProductionStatus.GOING_TO_JOB, i -> null);
        b.put(ProductionStatus.NO_SUPPLIES, i -> i.wi.simulateRecoverInsertedItems(i.inState.town()));
        b.put(ProductionStatus.IDLE, i -> null);
        b.put(ProductionStatus.NO_JOBSITE, i -> null);
        b.put(ProductionStatus.NO_WORK_POSSIBLE, i -> null);
        handler = b.build();
    }

    public static Warper<ServerLevel, MCTownState> warper(
            TimeWarpWorldInteraction wi,
            int maxState,
            boolean prioritizeExtraction,
            @Nullable SlotPrecondition slotPrecondition
    ) {
        ImmutableSet<ProductionStatus> c = handler.keySet();
        ImmutableSet<ProductionStatus> productionStatuses = ProductionStatus.allStatuses();
        if (!c.containsAll(productionStatuses)) {
            throw new IllegalStateException("Not all production states are handled. Difference: " + Sets.difference(
                    ImmutableSet.copyOf(productionStatuses),
                    ImmutableSet.copyOf(c)
            ));
        }

        return new Warper<>() {
            boolean cycleCompleted = false;

            @Override
            public boolean isCycleComplete() {
                return cycleCompleted;
            }

            @Override
            public MCTownState warp(
                    ServerLevel level,
                    MCTownState inState,
                    long currentTick,
                    long ticksPassed,
                    int villagerNum
            ) {
                BlockPos workPos = wi.shouldUseRealWorkBlock()
                        ? wi.getAssignedWorkBlock()
                        : new BlockPos(villagerNum, villagerNum, villagerNum);

                MCTownState outState = inState;

                State state = outState.workStates.get(workPos);
                if (state == null) {
                    outState = outState.setJobBlockState(workPos, State.fresh());
                }

                boolean freshCycle = outState.workStates.get(workPos).processingState() == 0;
                if (freshCycle
                        && slotPrecondition != null
                        && wi.shouldUseRealWorkBlock()) {
                    ca.bradj.questown.world.QTWorldAccess ww = wi.getWarpWorld();
                    if (ww != null) {
                        if (!slotPrecondition.test(ww, workPos)) {
                            return inState;
                        }
                    } else {
                        BlockEntity entity = level.getBlockEntity(workPos);
                        if (entity != null && !slotPrecondition.test(entity)) {
                            return inState;
                        }
                    }
                }

                ProductionStatus status = ProductionStatus.FACTORY.idle();

                final State ztate = outState.workStates.get(workPos);

                final TimeWarpWorldInteraction.Inputs fState = new TimeWarpWorldInteraction.Inputs(
                        outState,
                        level,
                        inState.getVillager(villagerNum).uuid
                );
                wi.injectTicks((int) ticksPassed);
                MCRoom fakeRoom = Spaces.metaRoomAround(workPos, 1);
                final long VIRTUAL_MORNING_TICK = 1000;
                @Nullable ProductionStatus nuStatus = ProductionStatuses.getNewStatusFromSignal(
                        status,
                        Signals.fromDayTime(new Signals.DayTime(VIRTUAL_MORNING_TICK)),
                        wi.asInventory(() -> wi.getHeldItems(fState, villagerNum), ztate::processingState),
                        wi.asTownJobs(
                                ztate,
                                new RoomRecipeMatch<>(
                                        fakeRoom,
                                        ImmutableList.of(new ResourceLocation("fake")),
                                        ImmutableList.of()
                                ),
                                workPos,
                                outState.containers,
                                () -> Util.getDayTime(level)
                        ),
                        DeclarativeJobs.alwaysInRoom(fakeRoom),
                        STATUS_FACTORY,
                        prioritizeExtraction
                );
                if (nuStatus != null) {
                    status = nuStatus;
                }
                MCTownState affectedState = handler.get(status).apply(new HandlerInputs(
                        wi,
                        fState,
                        status,
                        ztate,
                        maxState,
                        workPos
                ));
                if (affectedState != null) {
                    outState = affectedState;
                    if (status.isExtractingProduct()) {
                        cycleCompleted = true;
                    }
                }

                State afterState = outState.workStates.get(workPos);
                if (afterState != null
                        && afterState.processingState() >= maxState
                        && afterState.workLeft() == 0
                        && !status.isExtractingProduct()) {
                    TimeWarpWorldInteraction.Inputs extractInputs =
                            new TimeWarpWorldInteraction.Inputs(outState, level, inState.getVillager(villagerNum).uuid);
                    MCTownState extracted = handler.get(ProductionStatus.EXTRACTING_PRODUCT).apply(new HandlerInputs(
                            wi, extractInputs, ProductionStatus.EXTRACTING_PRODUCT,
                            afterState, maxState, workPos
                    ));
                    if (extracted != null) {
                        outState = extracted;
                        cycleCompleted = true;
                    }
                }

                outState = outState.withTimerReducedBy(workPos, (int) ticksPassed);

                return outState;
            }

            @Override
            public Collection<Tick> getTicks(
                    long referenceTick,
                    long ticksPassed
            ) {
                ImmutableList.Builder<Tick> b = ImmutableList.builder();

                long start = referenceTick;
                long max = referenceTick + ticksPassed;

                // TODO[WARP]: Factor in timers and "walk time"
                int workInterval = wi.interval * 2; // Doubling as a heuristic to simulate walking
                int stepInterval = Math.max(workInterval, 100); // 100 As a heuristic for walking time
                for (long i = start; i <= max; i += stepInterval) {
                    b.add(new Tick(i, stepInterval));
                }
                return b.build();
            }
        };
    }

    private static EntityLocStateProvider<MCRoom> alwaysInRoom(
            MCRoom fakeRoom
    ) {
        return new EntityLocStateProvider<MCRoom>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                return fakeRoom;
            }
        };
    }

    /**
     * Finds rooms that contain at least one block matching both predicates.
     * Package-private to consolidate declarative job logic within this package.
     *
     * @param rooms           The rooms to search
     * @param isJobBlock      Predicate to check if a position is a job block
     * @param hasCorrectState Predicate to check if a position has the desired state
     * @return Rooms containing at least one matching block
     */
    static <ROOM extends Room, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> ImmutableList<MATCH> roomsWithState(
            Collection<MATCH> rooms,
            Predicate<POS> isJobBlock,
            Predicate<POS> hasCorrectState
    ) {
        ImmutableList.Builder<MATCH> result = ImmutableList.builder();
        for (MATCH room : rooms) {
            if (roomHasMatchingBlock(room, isJobBlock, hasCorrectState)) {
                result.add(room);
            }
        }
        return result.build();
    }

    private static <POS, MATCH extends IRoomRecipeMatch<?, ?, POS, ?>> boolean roomHasMatchingBlock(
            MATCH room,
            Predicate<POS> isJobBlock,
            Predicate<POS> hasCorrectState
    ) {
        boolean matched = false;
        for (POS pos : room.getContainedBlocks().keySet()) {
            if (isJobBlock.test(pos) && hasCorrectState.test(pos)) {
                matched = true;
                break;
            }
        }
        return matched;
    }


// ---- moved verbatim from ca.bradj.questown.jobs.JobsClean (consolidation plan C5, 2026-10-09) ----

// TODO: Test "should not return null if entity is in room with finished product"
    private static boolean isNearPosition(Position a, Position b, int maxDistance) {
        int dx = Math.abs(a.x - b.x);
        int dz = Math.abs(a.z - b.z);
        return dx <= maxDistance && dz <= maxDistance;
    }

    public static <ROOM extends Room, RECIPE, POS> EntityCurrentJobSite<ROOM> getEntityCurrentJobSite(
            // TODO: Consider y coordinate
            Position entityBlockPos,
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> roomsNeedingIngredientsOrTools,
            Collection<ROOM> roomsWithCompletedProduct,
            Predicate<ROOM> additionalPosCheck,
            Predicate<RECIPE> isFarm
    ) {
        for (ROOM room : roomsWithCompletedProduct) {
            boolean contains = InclusiveSpaces.contains(room.getSpaces(), entityBlockPos);
            boolean atDoor = room.getDoorPos().equals(entityBlockPos);
            boolean nearDoor = isNearPosition(entityBlockPos, room.getDoorPos(), 2);
            if (contains || atDoor || nearDoor) {
                return new EntityCurrentJobSite<>(room, false); // TODO: Add a check for farm
            }
        }

        // TODO: Support multiple tiers of job site (i.e. more than one resource location)
        Predicate<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> containsEntity = v ->
        {
            boolean contains = InclusiveSpaces.contains(v.getRoom().getSpaces(), entityBlockPos);
            return contains || v.getRoom().getDoorPos().equals(entityBlockPos);
        };
        // FIXME: We should also check rooms needing WORK
        return roomsNeedingIngredientsOrTools
                .getMatches()
                .stream()
                .map(RoomsNeedingVillagerInput.NVIRoom::room)
                .filter(v -> additionalPosCheck.test(v.getRoom()))
                .filter(containsEntity)
                .findFirst()
                .map(v -> new EntityCurrentJobSite<>(v.getRoom(), v.getRecipeIDs().stream().anyMatch(isFarm)))
                .orElse(null);
    }

    public static <ROOM extends Room, RECIPE, BLOCK> @NotNull WithReason<@Nullable BLOCK> findJobSite(
            int maxState,
            boolean prioritizeExtraction,
            Map<Integer, SupplyItemStatus> statusItems,
            Collection<ROOM> roomsWithFinishedProduct,
            Function<ROOM, BLOCK> getPositionWithin,
            RoomsNeedingVillagerInput<ROOM, RECIPE, BLOCK> blocksSrc,
            Function<BLOCK, State> work,
            Predicate<BLOCK> isJobBlock,
            BiFunction<BLOCK, ROOM, BLOCK> findInteractionSpot
    ) {
        if (prioritizeExtraction && !roomsWithFinishedProduct.isEmpty()) {
            return WithReason.always(
                    getPositionWithin.apply(roomsWithFinishedProduct.iterator().next()),
                    "prioritizing extraction and room has result"
            );
        }

        ArrayList<NVIRoom<ROOM, RECIPE, BLOCK>> rooms = new ArrayList<>(blocksSrc.getMatches());
        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        boolean roomFoundButNotBlock = true;

        for (NVIRoom<ROOM, RECIPE, BLOCK> match : rooms) {
            for (Map.Entry<BLOCK, ?> blocks : match.room().getContainedBlocks().entrySet()
            ) {
                BLOCK blockPos = blocks.getKey();
                State blockState = work.apply(blockPos);
                if (blockState == null) {
                    blockState = State.freshAtState(0);
                } else if (blockState.processingState() > maxState) {
                    // Foreign work's record (shared block state slot, different numbering):
                    // out of this job's range it matches no bucket - read as fresh, the
                    // same as the gate path (rooms above) and the supply-loop guard.
                    blockState = State.freshAtState(0);
                }
                if (isJobBlock.test(blockPos)) {
                    roomFoundButNotBlock = false;
                } else {
                    continue;
                }

                Supplier<BLOCK> is = () -> findInteractionSpot.apply(
                        blockPos,
                        match.room().getRoom()
                );

                if (maxState == blockState.processingState()) {
                    return new WithReason<>(is.get(), "Found extractable product");
                }
                SupplyItemStatus sis = statusItems.getOrDefault(blockState.processingState(), SupplyItemStatus.NOT_REQUIRED);
                if (sis != SupplyItemStatus.NEEDS_ITEM) {
                    return new WithReason<>(is.get(), "Found a spot where a held item can be used");
                }
            }
        }

        if (roomFoundButNotBlock) {
            return new WithReason<>(null, "Job site found, but no usable job blocks");
        }

        return new WithReason<>(null, "No job sites");
    }

    public static <POS, ROOM extends Room, RECIPE> ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<POS>> rooms(
            Supplier<ImmutableList<NVIRoom<ROOM, RECIPE, POS>>> jobSites,
            Function<POS, State> jobBlockStates,
            Predicate<POS> isJobBlock,
            Function<POS, String> stringify,
            int maxState
    ) {
        ImmutableMap.Builder<Integer, RoomsWithWorkableStatefulBlocks<POS>> b = ImmutableMap.builder();
        Supplier<Rooms<POS, ?>> e = () -> {
            ImmutableMap.Builder<POS, Integer> spotStatuses = ImmutableMap.builder();
            ImmutableMap.Builder<POS, Boolean> spotJBs = ImmutableMap.builder();
            Map<ROOM, List<Integer>> roomStatuses = new HashMap<>();
            Stream<NVIRoom<ROOM, RECIPE, POS>> rooms = jobSites.get().stream();

            //TODO: Validate that this is actually needed
            rooms = rooms.filter(v -> !v.dueToWorkOnly());

            rooms.forEach(match -> {
                for (Map.Entry<POS, ?> entry : match.room().getContainedBlocks().entrySet()) {
                    POS bp = entry.getKey();
                    State jobBlockState = jobBlockStates.apply(bp);
                    // A block with no work-state record in this store is unstarted work, not
                    // "no state": this read must agree with sibling sites (findJobSpot l.283,
                    // DeclarativeJobTicker bsFn l.203 and the rni guard l.279). Skipping nulls
                    // here deadlocked leavers whose per-owner store had not yet seeded the
                    // gate mat (NO_JOBSITE soft-lock, 2026-10-08, WorkStatusStoreMatSeedTest).
                    if (jobBlockState == null) {
                        jobBlockState = State.freshAtState(0);
                    } else if (jobBlockState.processingState() > maxState) {
                        // A record written by another work's state machine at this block
                        // (shared state slot, different numbering - e.g. downtime's 0-9 vs
                        // bop_depositing's 0-2). Out of this job's range it can never
                        // advance itself; read it as fresh, same as the supply-loop guard
                        // (DeclarativeJobTicker.computeRoomsNeedingInput).
                        jobBlockState = State.freshAtState(0);
                    }
                    int v = jobBlockState.processingState();
                    spotStatuses.put(bp, v);
                    UtilClean.addOrInitializeList(roomStatuses, match.room().getRoom(), v);
                    spotJBs.put(bp, isJobBlock.test(bp));
                }
            });
            return new Rooms<>(spotStatuses.build(), roomStatuses, spotJBs.build());
        };

        for (int i = 0; i < maxState; i++) {
            b.put(i, new RoomsWithWorkableStatefulBlocks<>(i, e, stringify));
        }
        return b.build();
    }

    public static <POS, MATCH extends IRoomRecipeMatch<?, ?, POS, ?>> boolean isUnfinishedTimeWorkPresent(
            Supplier<ImmutableList<MATCH>> roomSource,
            Function<POS, Integer> ticksSource
    ) {
        ImmutableList<MATCH> rooms = roomSource.get();
        return rooms.stream()
                    .anyMatch(v -> {
                        for (Map.Entry<POS, ?> e : v.getContainedBlocks().entrySet()) {
                            @Nullable Integer apply = ticksSource.apply(e.getKey());
                            if (apply != null && apply > 0) {
                                return true;
                            }
                        }
                        return false;
                    });
    }

    /**
     * Whether declared work at {@code state} is unfinished work. A block at {@code maxState}
     * holds a finished product awaiting extraction, never work: the hand-coded diner jobs
     * declare {@code DONE: 0} work at their terminal state, and counting it sent a diner whose
     * meal was ready "to the job site" instead of eating it (playtest 2026-10-17;
     * eating/eat_direct).
     */
    public static boolean declaresUnfinishedWork(int state, int maxState, boolean workDeclared) {
        return workDeclared && state < maxState;
    }

    public static <POS> Collection<Integer> getStatesWithUnfinishedWork(
            Collection<? extends Supplier<Collection<POS>>> rooms,
            Function<POS, State> getJobBlockState,
            Predicate<POS> canClaim,
            Function<Integer, Boolean> workDeclaredAt
    ) {
        HashSet<Integer> b = new HashSet<>();
        rooms.forEach(v -> {
            for (POS e : v.get()) {
                if (!canClaim.test(e)) {
                    continue;
                }
                @Nullable State apply = getJobBlockState.apply(e);
                // A state that declares work counts as unfinished even on a fresh block
                // (workLeft 0): a work-only state (cook_extract: 0.1 floors to int 0) would
                // otherwise never report itself and the itemless-work branch would stall.
                if (apply != null
                        && (apply.workLeft() > 0 || workDeclaredAt.apply(apply.processingState()))) {
                    b.add(apply.processingState());
                    return;
                }
            }
        });
        ArrayList<Integer> b2 = new ArrayList<>(b);
        Collections.sort(b2);
        return ImmutableList.copyOf(b2);
    }
}
