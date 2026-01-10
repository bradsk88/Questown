package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.AbstractDeclarativeJobWarper.StatusProvider;
import ca.bradj.questown.jobs.declarative.NeedsRegistrations;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class IntDeclarativeJobWarpingTest {

    private static final Map<ProductionStatus, Collection<String>> NO_RULES =
            ImmutableMap.of();

    private static final EntityLocStateProvider<Room> NO_LOCATION = () -> null;

    private static final DeclarativeJobChecks<
            TInputs, GathererJournalTest.TestItem, GathererJournalTest.TestItem, ?, TownPosition
    > EMPTY_CHECKS = new DeclarativeJobChecks<>(
            ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
            ImmutableMap.of(), ImmutableMap.of(),
            room -> false, block -> false
    );

    // Simple provider implementations
    static class TestInventoryProvider implements EntityInvStateProvider<Integer> {
        @Override public boolean inventoryFull() { return false; }
        @Override public boolean hasNonSupplyItems() { return false; }
        @Override public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
            return Map.of();
        }
    }

    static class TestTownProvider implements JobTownProvider<Room> {
        @Override public boolean hasSupplies() { return true; }
        @Override public boolean hasSpace() { return false; }
        @Override public Collection<Room> roomsWithCompletedProduct() {
            return List.of();
        }
        @Override public RoomsNeedingVillagerInput<Room, ?, ?> roomsNeedingIngredientsByState() {
            return null;
        }
        @Override public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
            return Map.of();
        }
        @Override public LZCD.Dependency<Void> hasSuppliesV2() {
            return new ConstantDep("has supplies [test]", true);
        }
        @Override public boolean isUnfinishedTimeWorkPresent() { return false; }
        @Override public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
            return List.of();
        }
        @Override public Collection<Room> roomsAtState(Integer state) {
            return List.of();
        }
    }

    static class TInputs extends Inpoots<TestWorkSpotStandIn, Object> {
        public TInputs(
                TestWorkSpotStandIn town,
                @Nullable Room entityLocation,
                UUID entityUUID
        ) {
            super(town, entityLocation, entityUUID);
        }
    }

    private static class THandler extends AbstractStateInteraction<
            TInputs, TownPosition, GathererJournalTest.TestItem,
            GathererJournalTest.TestItem, TestWorkSpotStandIn> {

        List<String> heldItems = new ArrayList<>();

        // Track which handler methods were called
        boolean dropLootCalled = false;
        boolean collectSuppliesCalled = false;
        boolean tryWorkingCalled = false;

        THandler(
                JobID jobId, int villagerIndex, int interval, int maxState,
                DeclarativeJobChecks<TInputs, GathererJournalTest.TestItem,
                        GathererJournalTest.TestItem, ?, TownPosition> checks,
                Function<TInputs, Claim> claimSpots,
                Map<ProductionStatus, Collection<String>> specialRules
        ) {
            super(jobId, villagerIndex, interval, maxState, checks,
                    claimSpots, specialRules);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        static AbstractStateInteraction<Inpoots<TestWorkSpotStandIn, Object>,
                TownPosition, ?, ?, TestWorkSpotStandIn> make(
                JobID jobID, int idx, int interval, int maxState,
                DeclarativeJobChecks<TInputs, GathererJournalTest.TestItem,
                        GathererJournalTest.TestItem, ?, TownPosition> checks,
                Function<TInputs, Claim> claimSpots,
                Map<ProductionStatus, Collection<String>> rules
        ) {
            return (AbstractStateInteraction) new THandler(
                    jobID, idx, interval, maxState, checks, claimSpots, rules
            );
        }

        // --- Required overrides with real logic ---

        @Override
        public TestWorkSpotStandIn simulateDropLoot(
                TestWorkSpotStandIn s, ProductionStatus status) {
            dropLootCalled = true;
            heldItems = heldItems.stream()
                    .filter(i -> !"result_item".equals(i))
                    .collect(Collectors.toList());
            return s;
        }

        @Override
        public @Nullable TestWorkSpotStandIn simulateCollectSupplies(
                TestWorkSpotStandIn s, int processingState) {
            collectSuppliesCalled = true;
            heldItems.add("supply_item");
            return s;
        }

        @Override
        protected void iterate(
                Iterable<GathererJournalTest.TestItem> src,
                Function<GathererJournalTest.TestItem, GathererJournalTest.TestItem> push
        ) {
            Util.iterate(src, push::apply);
        }

        @Override
        protected TestWorkSpotStandIn setHeldItem(
                TInputs x, TestWorkSpotStandIn t, int vIdx, int iIdx,
                GathererJournalTest.TestItem item) {
            heldItems.set(iIdx, item.value);
            return t;
        }

        @Override
        protected WorkedSpot<TownPosition> getWorkedSpotWithUpToDateState(
                TInputs x, TestWorkSpotStandIn src, TownPosition ws) {
            return new WorkedSpot<>(ws, src.state.processingState());
        }

        @Override
        protected Collection<GathererJournalTest.TestItem> getHeldItems(
                TInputs x, int villagerIndex) {
            return heldItems.stream()
                    .map(GathererJournalTest.TestItem::new).toList();
        }

        @Override
        protected TestWorkSpotStandIn setJobBlockState(
                @NotNull TInputs x, TestWorkSpotStandIn ts,
                TownPosition pos, State fresh) {
            ts.state = fresh;
            return ts;
        }

        @Override
        protected ImmutableList<GathererJournalTest.TestItem> getResults(
                TInputs x, Collection<GathererJournalTest.TestItem> items) {
            return ImmutableList.of(new GathererJournalTest.TestItem("result"));
        }

        @Override
        public boolean tryGrabbingInsertedSupplies(TInputs extra) {
            boolean grabbed = false;
            for (String item : extra.town().insertedItems) {
                grabbed = true;
                heldItems.add(item);
            }
            extra.town().insertedItems = new ArrayList<>();
            return grabbed;
        }

        @Override
        public int timesInserted(TInputs x) {
            return x.town().insertedItems.size();
        }

        @Override
        public @Nullable WorkOutput<@Nullable TestWorkSpotStandIn, WorkPosition<TownPosition>> tryWorking(
                TInputs extra,
                WorkPosition<TownPosition> workSpot
        ) {
            tryWorkingCalled = true;
            // Actually reduce work on the state
            TestWorkSpotStandIn town = extra.town();
            if (town.state != null && town.state.hasWorkLeft()) {
                town.state = town.state.decrWork(10); // Apply max work
                // Consume a supply item when work completes (realistic behavior)
                if (!town.state.hasWorkLeft()) {
                    heldItems.remove("supply_item");
                }
            }
            return new WorkOutput<>(true, false, town, workSpot);
        }

        // --- Simple stubs ---

        @Override protected int getWorkSpeedOf10(TInputs x) { return 10; }
        @Override protected int getAffectedTime(TInputs x, Integer t) { return t; }
        @Override protected TownPosition getTownPos(TInputs x) { return x.town().get(); }
        @Override protected TestWorkSpotStandIn getTown(TInputs x) { return x.town(); }
        @Override protected boolean isInstanze(GathererJournalTest.TestItem i, Class<?> c) { return false; }
        @Override protected boolean isStacked(GathererJournalTest.TestItem i) { return false; }
        @Override protected boolean isWorkSpotReadyForItem(TInputs x, GathererJournalTest.TestItem i, TownPosition p) { return true; }
        @Override protected void triggerCompletionAdvancement(TInputs x, TownPosition p) {}
        @Override protected void registerUnmetNeed(TInputs x, NeedsRegistrations.Need n) {}
        @Override protected void registerUnmetRoom(TInputs x) {}

        @Override protected TestWorkSpotStandIn degradeTool(TInputs x, @Nullable TestWorkSpotStandIn t, PredicateCollection<GathererJournalTest.TestItem, ?> p) { return t; }
        @Override protected TestWorkSpotStandIn withEffectApplied(@NotNull TInputs x, TestWorkSpotStandIn t, GathererJournalTest.TestItem i) { return t; }
        @Override protected TestWorkSpotStandIn withKnowledge(@NotNull TInputs x, TestWorkSpotStandIn t, GathererJournalTest.TestItem i) { return t; }
        @Override protected WorkOutput<TestWorkSpotStandIn, WorkPosition<TownPosition>> getWithSurfaceInteractionPos(TInputs x, WorkOutput<TestWorkSpotStandIn, WorkPosition<TownPosition>> v) { return v; }
        @Override protected ArrayList<WorkPosition<TownPosition>> makeMutableShuffledCopy(TInputs x, Collection<WorkPosition<TownPosition>> ws) { return new ArrayList<>(ws); }

        @Override protected ImmutableWorkStateContainer<TownPosition, TestWorkSpotStandIn> getWorkStatuses(TInputs x) { return null; }
        @Override protected @Nullable TestWorkSpotStandIn postInsertHook(@NotNull TestWorkSpotStandIn t, Collection<String> r, TInputs x, WorkedSpot<TownPosition> p, GathererJournalTest.TestItem i) { return null; }
        @Override protected @Nullable TestWorkSpotStandIn preExtractHook(TestWorkSpotStandIn t, Collection<String> r, TInputs x, TownPosition p) { return null; }
        @Override protected TestWorkSpotStandIn postExtractHook(TestWorkSpotStandIn t, Collection<String> r, TInputs x, TownPosition p, GathererJournalTest.TestItem i) { return null; }
        @Override protected void preStateChangeHooks(@NotNull TestWorkSpotStandIn t, Collection<String> r, TInputs x, WorkSpot<Integer, TownPosition> p) {}
    }

    static class TestWorkSpotStandIn implements
            AbstractDeclarativeJobWarper.WorkSpotStandIn<TestWorkSpotStandIn, TownPosition> {

        public List<String> insertedItems = new ArrayList<>();
        long timer = 0;
        State state = State.fresh();

        @Override
        public TownPosition get() {
            return new TownPosition(0, 0, 0);
        }

        @Override
        public State getState(TestWorkSpotStandIn townState) {
            return townState.state;
        }

        @Override
        public TestWorkSpotStandIn setState(
                TestWorkSpotStandIn outState,
                State newValue
        ) {
            outState.state = newValue;
            return outState;
        }

        @Override
        public TestWorkSpotStandIn withTimerReducedBy(
                TestWorkSpotStandIn outState,
                int ticksPassed
        ) {
            outState.timer = Math.max(0, outState.timer - ticksPassed);
            return outState;
        }
    }

    static class TWarper extends
            AbstractDeclarativeJobWarper<TestWorkSpotStandIn, Room, TownPosition, Object> {

        TWarper() {
            super(); // Uses default status provider
        }

        TWarper(StatusProvider<Room> statusProvider) {
            super(statusProvider);
        }

        TWarper(StatusProvider<Room> statusProvider, boolean strictMode) {
            super(statusProvider, strictMode);
        }
    }

    // --- Test Helpers ---

    /**
     * Simple warp for timer tests - uses default status provider
     */
    private static TestWorkSpotStandIn warp(
            TestWorkSpotStandIn workspot,
            int ticksPassed
    ) {
        return warpWithStatus(workspot, ticksPassed, null, null);
    }

    /**
     * Warp with explicit status injection - for handler dispatch tests
     */
    private static TestWorkSpotStandIn warpWithStatus(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus status,
            @Nullable THandler handler
    ) {
        return warpWithStatus(workspot, gameTick, status, handler, false);
    }

    /**
     * Warp with explicit status injection and strict mode flag
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TestWorkSpotStandIn warpWithStatus(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus status,
            @Nullable THandler handler,
            boolean strictMode
    ) {
        UUID villagerID = UUID.randomUUID();
        AbstractDeclarativeJobWarper.staticInitialize();
        AbstractStateInteraction wi = handler != null ? handler : THandler.make(
                new JobID("test", "job"), 0, 0, 0,
                EMPTY_CHECKS,
                inpoots -> new Claim(villagerID, Long.MAX_VALUE),
                NO_RULES
        );

        // Inject status directly - no complex provider setup needed
        StatusProvider<Room> statusProvider = status != null
                ? (cur, sig, inv, town, loc, pri) -> status
                : (cur, sig, inv, town, loc, pri) -> null; // null = no status change

        return new TWarper(statusProvider, strictMode).warp(
                workspot,
                workspot,
                Warper.Tick.at(gameTick).after(gameTick),
                t -> new TestInventoryProvider(),
                t -> new TestTownProvider(),
                NO_LOCATION,
                false,
                wi,
                s -> new TInputs(s, null, villagerID),
                10
        );
    }

    // --- Tests ---

    @Test
    public void testShouldReduceTimersByTicksPassed() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.timer = 200;

        TestWorkSpotStandIn next = warp(workspot, 100);

        Assertions.assertEquals(100, next.timer);
    }

    @Test
    public void testShouldReduceTimersToZeroAtMost() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.timer = 200;

        TestWorkSpotStandIn next = warp(workspot, 201);

        Assertions.assertEquals(0, next.timer);
    }

    @Test
    public void testShouldHandleNullState() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = null;

        TestWorkSpotStandIn next = warp(workspot, 100);

        Assertions.assertEquals(State.fresh(), next.state);
    }

    // --- Handler Dispatch Tests ---
    // Note: Status detection is tested in StatusesProductionRoutineTest.
    // These tests verify that once a status is determined, the correct handler
    // is called. Status is injected directly via constructor - no need for
    // complex provider setups.

    private static THandler makeHandler() {
        return new THandler(
                new JobID("test", "job"), 0, 0, 0,
                EMPTY_CHECKS,
                i -> new Claim(UUID.randomUUID(), Long.MAX_VALUE),
                NO_RULES
        );
    }

    @Test
    public void whenDropLootStatus_shouldCallSimulateDropLoot() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // Inject DROPPING_LOOT status directly
        warpWithStatus(workspot, 100, ProductionStatus.DROPPING_LOOT, handler);

        Assertions.assertTrue(
                handler.dropLootCalled,
                "Expected simulateDropLoot to be called for DROPPING_LOOT status"
        );
    }

    @Test
    public void whenCollectSuppliesStatus_shouldCallSimulateCollectSupplies() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // Inject COLLECTING_SUPPLIES status directly
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);

        Assertions.assertTrue(
                handler.collectSuppliesCalled,
                "Expected simulateCollectSupplies to be called for COLLECTING_SUPPLIES status"
        );
    }

    @Test
    public void whenIdleStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // Inject IDLE status directly (no-op handler)
        warpWithStatus(workspot, 100, ProductionStatus.IDLE, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenExtractingProductStatus_shouldCallTryWorking() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // Inject EXTRACTING_PRODUCT status directly
        warpWithStatus(workspot, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);

        Assertions.assertTrue(
                handler.tryWorkingCalled,
                "Expected tryWorking to be called for EXTRACTING_PRODUCT status"
        );
    }

    @Test
    public void whenNoSpaceStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.NO_SPACE, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenNoJobsiteStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.NO_JOBSITE, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenWaitingForTimedStateStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenGoingToJobStatus_inStrictMode_shouldThrowException() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // GOING_TO_JOB is invalid during warp because the warper assumes
        // workers are already at their jobsite
        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> warpWithStatus(workspot, 100, ProductionStatus.GOING_TO_JOB, handler, true)
        );

        Assertions.assertTrue(
                exception.getMessage().contains("GOING_TO_JOB"),
                "Exception message should mention GOING_TO_JOB"
        );
    }

    @Test
    public void whenGoingToJobStatus_notStrictMode_shouldNotCallAnyHandler() {
        // In non-strict mode (runtime), GOING_TO_JOB is a no-op to avoid crashes
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.GOING_TO_JOB, handler, false);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenNoSuppliesStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.NO_SUPPLIES, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenRelaxingStatus_shouldNotCallAnyHandler() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.RELAXING, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenNoWorkPossibleStatus_shouldNotCallAnyHandler() {
        // NO_WORK_POSSIBLE occurs when a villager is at the job board seeking work
        // but the town has no available jobs matching their skills.
        // During time warp, this status should do nothing (uses NULL_HENDLAR).
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        warpWithStatus(workspot, 100, ProductionStatus.NO_WORK_POSSIBLE, handler);

        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
        Assertions.assertFalse(handler.tryWorkingCalled, "tryWorking should not be called");
    }

    @Test
    public void whenWorkState_shouldCallTryWorking() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();

        // Work state 0 (first production state)
        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);
        warpWithStatus(workspot, 100, workState, handler);

        Assertions.assertTrue(
                handler.tryWorkingCalled,
                "Expected tryWorking to be called for work state 0"
        );
    }

    // --- Multi-Warp Scenario Tests ---

    @Test
    public void multiWarp_shouldAccumulateTimerReductions() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.timer = 300;

        // Warp three times
        TestWorkSpotStandIn after1 = warp(workspot, 50);
        TestWorkSpotStandIn after2 = warp(after1, 75);
        TestWorkSpotStandIn after3 = warp(after2, 100);

        // 300 - 50 - 75 - 100 = 75
        Assertions.assertEquals(75, after3.timer);
    }

    @Test
    public void multiWarp_shouldAccumulateDropLootEffects() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("result_item", "result_item", "other"));

        // First drop
        warpWithStatus(workspot, 100, ProductionStatus.DROPPING_LOOT, handler);
        int afterFirst = handler.heldItems.size();

        // Second drop
        handler.heldItems.add("result_item");
        warpWithStatus(workspot, 100, ProductionStatus.DROPPING_LOOT, handler);
        int afterSecond = handler.heldItems.size();

        // All "result_item" entries should be removed, only "other" remains
        Assertions.assertEquals(1, afterFirst, "After first drop, only 'other' should remain");
        Assertions.assertEquals(1, afterSecond, "After second drop, still only 'other'");
    }

    @Test
    public void multiWarp_shouldAccumulateCollectSuppliesEffects() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Collect supplies multiple times
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);

        // Should have accumulated 3 supply items
        Assertions.assertEquals(3, handler.heldItems.size());
        Assertions.assertTrue(handler.heldItems.stream().allMatch("supply_item"::equals));
    }

    // --- Work Progression Tests ---
    // Note: State uses 10x internal scaling. setWorkLeft(N) stores N*10 internally.
    // decrWork(10) reduces internal by 10 (max allowed per call).
    // workLeft() returns ceil(internal/10).

    @Test
    public void workProgression_shouldReduceWorkWhenWorking() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(100);
        THandler handler = makeHandler();
        int initialWork = workspot.state.workLeft();

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn result = warpWithStatus(workspot, 100, workState, handler);

        // THandler.tryWorking calls decrWork(10), reducing internal by 10
        // Internal: 1000 -> 990, workLeft: 100 -> 99
        Assertions.assertTrue(
                result.state.workLeft() < initialWork,
                "Work should decrease after working"
        );
        Assertions.assertEquals(99, result.state.workLeft());
    }

    @Test
    public void workProgression_shouldNotReduceWorkBelowZero() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // Set work to 1 (internal = 10), one decrWork(10) should reach 0
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn result = warpWithStatus(workspot, 100, workState, handler);

        Assertions.assertEquals(0, result.state.workLeft(), "Work should reach zero");
        Assertions.assertTrue(
                result.state.workLeft() >= 0,
                "Work should not go below zero"
        );
    }

    @Test
    public void workProgression_multipleWarps_shouldProgressWork() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // Set work to 5 (internal = 50), need 5 decrWork(10) calls to reach 0
        workspot.state = State.fresh().setWorkLeft(5);
        THandler handler = makeHandler();

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);

        // Work 5 times (each decrWork(10) reduces internal by 10)
        TestWorkSpotStandIn current = workspot;
        for (int i = 0; i < 5; i++) {
            current = warpWithStatus(current, 100, workState, handler);
        }

        // Internal: 50 - (5 * 10) = 0
        Assertions.assertEquals(0, current.state.workLeft());
    }

    // --- Timer Reduction During Work State Tests ---

    @Test
    public void whenMidWorkWithActiveTimer_warpShouldReduceTimer() {
        // Scenario from logs: villager at state=2, workLeft=0, timer=2000
        // This represents a villager who has finished the work portion and is
        // waiting for the timer to complete before advancing to next state.
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(2).setWorkLeft(0);
        workspot.timer = 2000;
        THandler handler = makeHandler();

        // Warp with WAITING_FOR_TIMED_STATE (work done, waiting for timer)
        TestWorkSpotStandIn result = warpWithStatus(
                workspot, 500, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );

        // Timer should be reduced by ticks passed
        Assertions.assertEquals(1500, result.timer);
        // No handler actions should be called
        Assertions.assertFalse(handler.tryWorkingCalled);
        Assertions.assertFalse(handler.dropLootCalled);
        Assertions.assertFalse(handler.collectSuppliesCalled);
    }

    @Test
    public void whenMidWorkWithActiveTimer_multipleWarps_shouldAccumulateTimerReduction() {
        // Multiple warps should progressively reduce the timer
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(2).setWorkLeft(0);
        workspot.timer = 2000;
        THandler handler = makeHandler();

        // Warp multiple times
        TestWorkSpotStandIn after1 = warpWithStatus(
                workspot, 500, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );
        TestWorkSpotStandIn after2 = warpWithStatus(
                after1, 750, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );
        TestWorkSpotStandIn after3 = warpWithStatus(
                after2, 500, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );

        // 2000 - 500 - 750 - 500 = 250
        Assertions.assertEquals(250, after3.timer);
    }

    @Test
    public void whenMidWorkWithActiveTimer_warpExceedingTimer_shouldClampToZero() {
        // If warp ticks exceed remaining timer, timer should clamp to zero
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(2).setWorkLeft(0);
        workspot.timer = 500;
        THandler handler = makeHandler();

        // Warp more ticks than timer has remaining
        TestWorkSpotStandIn result = warpWithStatus(
                workspot, 1000, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );

        // Timer should be clamped to zero, not negative
        Assertions.assertEquals(0, result.timer);
    }

    // --- Full Cycle Tests ---
    // These tests verify complete work cycles through multiple status transitions

    @Test
    public void fullCycle_collectSupplies_shouldAddItemToInventory() {
        // Step 1 of cycle: Collect supplies from town containers
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Collect supplies
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);

        // Verify supply was added to inventory
        Assertions.assertEquals(1, handler.heldItems.size());
        Assertions.assertEquals("supply_item", handler.heldItems.get(0));
    }

    @Test
    public void fullCycle_workThenExtract_shouldProgressState() {
        // Steps 2-3: Work on block, then extract product
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1); // Minimal work
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("supply_item"));

        // Work on block (state 0)
        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn afterWork = warpWithStatus(workspot, 100, workState, handler);

        // Verify work was done
        Assertions.assertTrue(handler.tryWorkingCalled);
        Assertions.assertEquals(0, afterWork.state.workLeft());

        // Extract product
        warpWithStatus(afterWork, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);

        // tryWorking is called for extraction too
        Assertions.assertTrue(handler.tryWorkingCalled);
    }

    @Test
    public void fullCycle_dropLoot_shouldRemoveResultFromInventory() {
        // Step 4: Drop results to containers
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("result_item", "other_item"));

        // Drop loot
        warpWithStatus(workspot, 100, ProductionStatus.DROPPING_LOOT, handler);

        // Verify result was dropped (removed from inventory)
        Assertions.assertEquals(1, handler.heldItems.size());
        Assertions.assertEquals("other_item", handler.heldItems.get(0));
    }

    @Test
    public void fullCycle_completeSequence_shouldTransformSupplyToResult() {
        // Complete cycle: collect -> work -> extract -> drop
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // 1. Collect supplies
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Should have collected supply");

        // 2. Work on block
        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn afterWork = warpWithStatus(workspot, 100, workState, handler);
        Assertions.assertEquals(0, afterWork.state.workLeft(), "Work should be complete");

        // 3. Extract product (simulated - adds result_item)
        handler.heldItems.add("result_item"); // Simulate extraction result
        warpWithStatus(afterWork, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);

        // 4. Drop loot
        warpWithStatus(afterWork, 100, ProductionStatus.DROPPING_LOOT, handler);

        // Verify: supply_item was consumed during work, result_item was dropped
        Assertions.assertFalse(
                handler.heldItems.contains("supply_item"),
                "Supply item should be consumed during work"
        );
        Assertions.assertFalse(
                handler.heldItems.contains("result_item"),
                "Result item should be dropped"
        );
        Assertions.assertTrue(
                handler.heldItems.isEmpty(),
                "Inventory should be empty after full cycle"
        );
    }

    @Test
    public void fullCycle_multipleWorkSteps_shouldProgressThroughStates() {
        // Test progressing through multiple work states (0 -> 1 -> 2)
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();

        // Work at state 0
        ProductionStatus state0 = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn after0 = warpWithStatus(workspot, 100, state0, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should work at state 0");

        // Advance to state 1 and work
        after0.state = after0.state.setProcessing(1).setWorkLeft(1);
        handler.tryWorkingCalled = false;
        ProductionStatus state1 = ProductionStatus.fromJobBlockStatus(1);
        TestWorkSpotStandIn after1 = warpWithStatus(after0, 100, state1, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should work at state 1");

        // Advance to state 2 and work
        after1.state = after1.state.setProcessing(2).setWorkLeft(1);
        handler.tryWorkingCalled = false;
        ProductionStatus state2 = ProductionStatus.fromJobBlockStatus(2);
        TestWorkSpotStandIn after2 = warpWithStatus(after1, 100, state2, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should work at state 2");

        // All states processed
        Assertions.assertEquals(0, after2.state.workLeft());
    }

    @Test
    public void itemFlow_collectMultipleThenDropAll_shouldAccumulate() {
        // Collect multiple supplies, then drop all results
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Collect supplies 3 times
        for (int i = 0; i < 3; i++) {
            warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        }
        Assertions.assertEquals(3, handler.heldItems.size(), "Should have 3 supplies");

        // Add results and drop
        handler.heldItems.add("result_item");
        handler.heldItems.add("result_item");
        warpWithStatus(workspot, 100, ProductionStatus.DROPPING_LOOT, handler);

        // Only supplies should remain
        Assertions.assertEquals(3, handler.heldItems.size());
        Assertions.assertTrue(
                handler.heldItems.stream().allMatch("supply_item"::equals),
                "Only supply items should remain after dropping results"
        );
    }
}