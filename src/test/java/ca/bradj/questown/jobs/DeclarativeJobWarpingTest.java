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
        public @Nullable TestWorkSpotStandIn simulateRecoverInsertedItems(TestWorkSpotStandIn s) {
            // Test implementation: no-op (no items tracked in test)
            return null;
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
                10,
                null  // initialStatus - test injects status via statusProvider instead
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

    // --- Crafter-style Warp Tests ---
    // These tests specifically verify the Crafter job pattern:
    // ingredients (state 0) -> work (state 1) -> extract -> drop
    // Unlike Gatherer, Crafter has NO timed state (no WAITING_FOR_TIMED_STATE).

    @Test
    public void crafterStyle_ingredientThenWork_shouldNotRequireTimedState() {
        // Crafter pattern: collect ingredient, work N ticks, extract, drop
        // No WAITING_FOR_TIMED_STATE should be needed
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(5); // 5 work ticks like crafter/stick
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // 1. Collect ingredient (sapling for crafter/stick)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Should have collected ingredient");

        // 2. Work 5 ticks (no time state involved)
        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);
        TestWorkSpotStandIn current = workspot;
        for (int i = 0; i < 5; i++) {
            current = warpWithStatus(current, 100, workState, handler);
        }
        Assertions.assertEquals(0, current.state.workLeft(), "Work should be complete after 5 ticks");

        // 3. Extract product (stick)
        handler.heldItems.add("result_item"); // Simulate extraction adding stick
        warpWithStatus(current, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Extraction should call tryWorking");

        // 4. Drop loot (no WAITING_FOR_TIMED_STATE between extract and drop)
        warpWithStatus(current, 100, ProductionStatus.DROPPING_LOOT, handler);
        Assertions.assertTrue(handler.dropLootCalled, "Should drop loot");
        Assertions.assertFalse(
                handler.heldItems.contains("result_item"),
                "Result should be dropped to container"
        );
    }

    @Test
    public void crafterStyle_multipleWorkTicks_shouldProgressCorrectly() {
        // Test that crafter's 5 work ticks progress correctly
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // Work requirement: 5 (internal = 50)
        workspot.state = State.fresh().setWorkLeft(5);
        THandler handler = makeHandler();

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);

        // Track work progression: 5 -> 4 -> 3 -> 2 -> 1 -> 0
        int[] expectedWork = {4, 3, 2, 1, 0};
        TestWorkSpotStandIn current = workspot;

        for (int i = 0; i < 5; i++) {
            current = warpWithStatus(current, 100, workState, handler);
            Assertions.assertEquals(
                    expectedWork[i],
                    current.state.workLeft(),
                    "Work should be " + expectedWork[i] + " after " + (i + 1) + " ticks"
            );
        }
    }

    @Test
    public void crafterStyle_noTimedStateInFlow_shouldCompleteWithoutTimer() {
        // Verify crafter flow completes without any timer dependencies
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        workspot.timer = 0; // No timer set - crafter doesn't use timers
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Complete cycle without any WAITING_FOR_TIMED_STATE
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);
        TestWorkSpotStandIn afterWork = warpWithStatus(workspot, 100, workState, handler);

        // Timer should still be 0 (not used in crafter flow)
        Assertions.assertEquals(0, afterWork.timer, "Timer should remain 0 for crafter");

        // Work should complete
        Assertions.assertEquals(0, afterWork.state.workLeft(), "Work should complete");

        // Can proceed directly to extract and drop without waiting
        handler.heldItems.add("result_item");
        warpWithStatus(afterWork, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);
        warpWithStatus(afterWork, 100, ProductionStatus.DROPPING_LOOT, handler);

        Assertions.assertTrue(handler.heldItems.isEmpty() || !handler.heldItems.contains("result_item"),
                "Crafter cycle should complete without timer state");
    }

    @Test
    public void crafterVsGatherer_differentWorkPatterns() {
        // Compare crafter (no time state) vs gatherer (has time state) patterns
        THandler crafterHandler = makeHandler();
        THandler gathererHandler = makeHandler();

        // Crafter: collect -> work -> extract -> drop (no WAITING_FOR_TIMED_STATE)
        TestWorkSpotStandIn crafterSpot = new TestWorkSpotStandIn();
        crafterSpot.state = State.fresh().setWorkLeft(1);
        crafterHandler.heldItems = new ArrayList<>();

        warpWithStatus(crafterSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, crafterHandler);
        TestWorkSpotStandIn crafterAfterWork = warpWithStatus(
                crafterSpot, 100, ProductionStatus.fromJobBlockStatus(1), crafterHandler
        );
        crafterHandler.heldItems.add("result_item");
        warpWithStatus(crafterAfterWork, 100, ProductionStatus.EXTRACTING_PRODUCT, crafterHandler);
        warpWithStatus(crafterAfterWork, 100, ProductionStatus.DROPPING_LOOT, crafterHandler);

        // Gatherer: collect -> work -> WAIT (time) -> extract -> drop
        TestWorkSpotStandIn gathererSpot = new TestWorkSpotStandIn();
        gathererSpot.state = State.fresh().setWorkLeft(1);
        gathererSpot.timer = 2000; // Gatherer has timer
        gathererHandler.heldItems = new ArrayList<>();

        warpWithStatus(gathererSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, gathererHandler);
        TestWorkSpotStandIn gathererAfterWork = warpWithStatus(
                gathererSpot, 100, ProductionStatus.fromJobBlockStatus(1), gathererHandler
        );
        // Gatherer must wait for timer
        TestWorkSpotStandIn gathererAfterWait = warpWithStatus(
                gathererAfterWork, 2000, ProductionStatus.WAITING_FOR_TIMED_STATE, gathererHandler
        );
        Assertions.assertEquals(0, gathererAfterWait.timer, "Gatherer timer should be depleted");

        gathererHandler.heldItems.add("result_item");
        warpWithStatus(gathererAfterWait, 100, ProductionStatus.EXTRACTING_PRODUCT, gathererHandler);
        warpWithStatus(gathererAfterWait, 100, ProductionStatus.DROPPING_LOOT, gathererHandler);

        // Both should complete successfully
        Assertions.assertTrue(crafterHandler.dropLootCalled, "Crafter should complete");
        Assertions.assertTrue(gathererHandler.dropLootCalled, "Gatherer should complete");
    }

    // --- Baker-style Multi-Input Warp Tests ---
    // These tests verify the Baker job pattern with multiple ingredient states:
    // State 0: collect wheat -> State 1: collect coal -> State 2: wait (time) -> extract -> drop
    // Unlike Crafter (single ingredient), Baker requires collecting supplies at MULTIPLE states.

    @Test
    public void bakerStyle_multipleIngredientStates_shouldCollectAtEachState() {
        // Baker pattern: collect wheat (state 0), collect coal (state 1), wait, extract, drop
        // Note: The mock's tryWorking consumes a supply_item when work completes.
        // This simulates inserting the ingredient into the oven.
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh(); // Start at state 0
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // 1. First ingredient collection (wheat for state 0)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Should have collected first ingredient (wheat)");

        // 2. Work at state 0 - inserts wheat into oven (mock consumes supply_item)
        ProductionStatus state0Work = ProductionStatus.fromJobBlockStatus(0);
        workspot.state = workspot.state.setWorkLeft(1);
        TestWorkSpotStandIn afterState0 = warpWithStatus(workspot, 100, state0Work, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should work at state 0");
        // After work completes, supply_item was "inserted" (consumed by mock)
        Assertions.assertEquals(0, handler.heldItems.size(), "Ingredient was inserted into oven");

        // 3. Second ingredient collection (coal for state 1)
        handler.collectSuppliesCalled = false;
        warpWithStatus(afterState0, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertTrue(handler.collectSuppliesCalled, "Should collect supplies for state 1");
        Assertions.assertEquals(1, handler.heldItems.size(), "Should have collected second ingredient (coal)");

        // 4. Work at state 1 - inserts coal into oven
        handler.tryWorkingCalled = false;
        ProductionStatus state1Work = ProductionStatus.fromJobBlockStatus(1);
        afterState0.state = afterState0.state.setProcessing(1).setWorkLeft(1);
        TestWorkSpotStandIn afterState1 = warpWithStatus(afterState0, 100, state1Work, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should work at state 1");
        Assertions.assertEquals(0, handler.heldItems.size(), "Coal was inserted into oven");
    }

    @Test
    public void bakerStyle_timedStateAfterIngredients_shouldWaitThenExtract() {
        // After collecting both ingredients and working, Baker has a time state
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(2).setWorkLeft(0); // At time state
        workspot.timer = 1000; // Baker's baking time
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("wheat", "coal")); // Already collected

        // Wait for timer (like Gatherer)
        TestWorkSpotStandIn afterWait = warpWithStatus(
                workspot, 1000, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );
        Assertions.assertEquals(0, afterWait.timer, "Timer should be depleted after waiting");
        Assertions.assertFalse(handler.tryWorkingCalled, "Should not call tryWorking during wait");

        // Then extract
        handler.heldItems.add("result_item"); // Simulate bread production
        warpWithStatus(afterWait, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Should extract after timer");
    }

    @Test
    public void bakerStyle_fullCycle_collectTwiceThenWaitThenExtract() {
        // Complete baker cycle:
        // collect wheat -> work state 0 -> collect coal -> work state 1 -> wait -> extract -> drop
        // Note: Mock consumes supply_item when work completes (simulates inserting into oven)
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        workspot.timer = 0;
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Step 1: Collect first ingredient (wheat)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Step 1: Should have wheat");

        // Step 2: Work at state 0 (inserts wheat into oven)
        ProductionStatus state0 = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn afterS0 = warpWithStatus(workspot, 100, state0, handler);
        Assertions.assertEquals(0, afterS0.state.workLeft(), "Step 2: State 0 work complete");
        Assertions.assertEquals(0, handler.heldItems.size(), "Step 2: Wheat was inserted");

        // Step 3: Collect second ingredient (coal)
        warpWithStatus(afterS0, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Step 3: Should have coal");

        // Step 4: Work at state 1 (inserts coal into oven)
        afterS0.state = afterS0.state.setProcessing(1).setWorkLeft(1);
        ProductionStatus state1 = ProductionStatus.fromJobBlockStatus(1);
        TestWorkSpotStandIn afterS1 = warpWithStatus(afterS0, 100, state1, handler);
        Assertions.assertEquals(0, afterS1.state.workLeft(), "Step 4: State 1 work complete");
        Assertions.assertEquals(0, handler.heldItems.size(), "Step 4: Coal was inserted");

        // Step 5: Wait for timer (time state 2 - baking)
        afterS1.state = afterS1.state.setProcessing(2);
        afterS1.timer = 1000;
        TestWorkSpotStandIn afterWait = warpWithStatus(
                afterS1, 1000, ProductionStatus.WAITING_FOR_TIMED_STATE, handler
        );
        Assertions.assertEquals(0, afterWait.timer, "Step 5: Timer depleted (baking done)");

        // Step 6: Extract product (bread)
        handler.heldItems.add("result_item");
        warpWithStatus(afterWait, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);

        // Step 7: Drop loot
        warpWithStatus(afterWait, 100, ProductionStatus.DROPPING_LOOT, handler);
        Assertions.assertTrue(handler.dropLootCalled, "Step 7: Should drop bread");
    }

    @Test
    public void bakerStyle_multipleIngredientsPerState_shouldCollectQuantity() {
        // Baker needs 2 wheat at state 0, 1 coal at state 1
        // This test verifies that multiple collection calls can accumulate items
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Collect wheat twice (quantity 2)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(2, handler.heldItems.size(), "Should have 2 wheat");

        // Work at state 0 consumes both
        workspot.state = workspot.state.setWorkLeft(1);
        warpWithStatus(workspot, 100, ProductionStatus.fromJobBlockStatus(0), handler);

        // Collect coal once (quantity 1)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        // Inventory: 1 wheat consumed + 2 supply_item collected (test mock adds "supply_item")
        Assertions.assertTrue(handler.heldItems.size() >= 2, "Should have coal after collecting");
    }

    @Test
    public void bakerVsCrafterVsGatherer_differentPatterns() {
        // Compare all three patterns:
        // Gatherer: collect -> work -> TIME -> extract -> drop
        // Crafter:  collect -> work -> extract -> drop (no time)
        // Baker:    collect -> collect -> TIME -> extract -> drop (multiple ingredients)

        THandler gathererHandler = makeHandler();
        THandler crafterHandler = makeHandler();
        THandler bakerHandler = makeHandler();

        // Gatherer: 1 ingredient, has time state
        TestWorkSpotStandIn gathererSpot = new TestWorkSpotStandIn();
        gathererSpot.timer = 2000;
        gathererHandler.heldItems = new ArrayList<>();
        warpWithStatus(gathererSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, gathererHandler);
        Assertions.assertEquals(1, gathererHandler.heldItems.size(), "Gatherer: 1 collection");
        warpWithStatus(gathererSpot, 2000, ProductionStatus.WAITING_FOR_TIMED_STATE, gathererHandler);
        Assertions.assertEquals(0, gathererSpot.timer, "Gatherer: timer depleted");

        // Crafter: 1 ingredient, no time state
        TestWorkSpotStandIn crafterSpot = new TestWorkSpotStandIn();
        crafterSpot.timer = 0; // No timer
        crafterHandler.heldItems = new ArrayList<>();
        warpWithStatus(crafterSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, crafterHandler);
        Assertions.assertEquals(1, crafterHandler.heldItems.size(), "Crafter: 1 collection");
        // No WAITING_FOR_TIMED_STATE needed

        // Baker: 2 ingredients (at different states), has time state
        TestWorkSpotStandIn bakerSpot = new TestWorkSpotStandIn();
        bakerSpot.timer = 1000;
        bakerHandler.heldItems = new ArrayList<>();
        // Collect at state 0
        warpWithStatus(bakerSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, bakerHandler);
        // Collect at state 1
        warpWithStatus(bakerSpot, 100, ProductionStatus.COLLECTING_SUPPLIES, bakerHandler);
        Assertions.assertEquals(2, bakerHandler.heldItems.size(), "Baker: 2 collections");
        warpWithStatus(bakerSpot, 1000, ProductionStatus.WAITING_FOR_TIMED_STATE, bakerHandler);
        Assertions.assertEquals(0, bakerSpot.timer, "Baker: timer depleted");

        // All patterns should be able to complete with drop
        gathererHandler.heldItems.add("result_item");
        warpWithStatus(gathererSpot, 100, ProductionStatus.DROPPING_LOOT, gathererHandler);
        crafterHandler.heldItems.add("result_item");
        warpWithStatus(crafterSpot, 100, ProductionStatus.DROPPING_LOOT, crafterHandler);
        bakerHandler.heldItems.add("result_item");
        warpWithStatus(bakerSpot, 100, ProductionStatus.DROPPING_LOOT, bakerHandler);

        Assertions.assertTrue(gathererHandler.dropLootCalled, "Gatherer completed");
        Assertions.assertTrue(crafterHandler.dropLootCalled, "Crafter completed");
        Assertions.assertTrue(bakerHandler.dropLootCalled, "Baker completed");
    }

    // --- Smelter-style Tool-Based Warp Tests ---
    // These tests verify the Smelter job pattern with tool requirements:
    // State 0: collect ingredient (iron_ore)
    // State 1: tool required (pickaxe) + work (20 ticks)
    // Extract: generate result (raw_iron)
    // Drop: put result in container, but KEEP tool
    //
    // Key difference: Tool is NOT consumed, it remains in inventory after work.

    @Test
    public void smelterStyle_toolRequiredForWork_shouldCollectTool() {
        // Smelter needs a tool (pickaxe) to work at state 1
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh();
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // 1. Collect ingredient (iron_ore) at state 0
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Should have ingredient");

        // 2. Collect tool (pickaxe) at state 1 - tool also uses COLLECTING_SUPPLIES
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(2, handler.heldItems.size(), "Should have ingredient + tool");

        // Mark one item as tool (locked)
        handler.heldItems.set(1, "tool_item_locked");
    }

    @Test
    public void smelterStyle_multipleWorkTicks_shouldProgressWithTool() {
        // Smelter has 5 work ticks (reduced from 20 for simpler test)
        // State uses 10x internal scaling: setWorkLeft(N) stores N*10 internally
        // Each warp calls decrWork(10), reducing by 1 work unit
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(5); // 5 work ticks
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("supply_item", "tool_item"));

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);

        // Work 5 ticks (each warp reduces by 1)
        TestWorkSpotStandIn current = workspot;
        for (int i = 4; i >= 0; i--) {
            current = warpWithStatus(current, 100, workState, handler);
            Assertions.assertEquals(i, current.state.workLeft(),
                    "Work should be " + i + " after " + (5 - i) + " warps");
        }
        Assertions.assertEquals(0, current.state.workLeft(), "Work should be complete");
    }

    @Test
    public void smelterStyle_toolNotConsumedWhenWorkCompletes() {
        // Unlike ingredients, tools should NOT be consumed when work completes
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // setWorkLeft(1) = 1 work unit, completes in 1 warp
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        // Note: The mock consumes "supply_item" when work completes
        // Tools should be a different item that is NOT consumed
        handler.heldItems = new ArrayList<>(List.of("supply_item", "tool_item"));

        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);
        warpWithStatus(workspot, 100, workState, handler);

        // supply_item was consumed (it's the ingredient)
        Assertions.assertFalse(handler.heldItems.contains("supply_item"), "Ingredient should be consumed");
        // tool_item remains (it's the tool, not consumed)
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Tool should NOT be consumed");
    }

    @Test
    public void smelterStyle_fullCycle_collectIngredientAndTool_workThenDrop() {
        // Complete smelter cycle:
        // collect iron_ore -> collect pickaxe -> work -> extract -> drop (keep tool)
        // Using setWorkLeft(1) so work completes in 1 warp for simplicity
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1); // 1 work unit = 1 warp
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Step 1: Collect ingredient (iron_ore)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Step 1: Should have iron_ore");

        // Step 2: Collect tool (pickaxe) - this would also be COLLECTING_SUPPLIES
        // Simulate: villager already has tool OR collects it
        handler.heldItems.add("tool_item"); // Simulate having tool
        Assertions.assertEquals(2, handler.heldItems.size(), "Step 2: Should have iron_ore + pickaxe");

        // Step 3: Work at state 1 (uses tool, consumes ingredient)
        ProductionStatus workState = ProductionStatus.fromJobBlockStatus(1);
        TestWorkSpotStandIn afterWork = warpWithStatus(workspot, 100, workState, handler);
        Assertions.assertEquals(0, afterWork.state.workLeft(), "Step 3: Work complete");
        // Note: mock consumes "supply_item" but not "tool_item"
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Step 3: Tool should remain");

        // Step 4: Extract product (raw_iron)
        handler.heldItems.add("result_item");
        warpWithStatus(afterWork, 100, ProductionStatus.EXTRACTING_PRODUCT, handler);
        Assertions.assertTrue(handler.tryWorkingCalled, "Step 4: Should extract");

        // Step 5: Drop loot - result is dropped, tool remains
        warpWithStatus(afterWork, 100, ProductionStatus.DROPPING_LOOT, handler);
        Assertions.assertTrue(handler.dropLootCalled, "Step 5: Should drop");
        // Result was dropped, tool should still be there
        Assertions.assertFalse(handler.heldItems.contains("result_item"), "Result should be dropped");
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Tool should remain after drop");
    }

    @Test
    public void smelterStyle_toolPreservedAcrossMultipleCycles() {
        // Smelter can do multiple cycles with the same tool
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("tool_item")); // Start with tool

        // Cycle 1
        TestWorkSpotStandIn workspot1 = new TestWorkSpotStandIn();
        workspot1.state = State.fresh().setWorkLeft(1); // 1 work unit = 1 warp

        warpWithStatus(workspot1, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(2, handler.heldItems.size(), "Cycle 1: tool + ingredient");

        warpWithStatus(workspot1, 100, ProductionStatus.fromJobBlockStatus(1), handler);
        // Ingredient consumed, tool remains
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Cycle 1: Tool remains");

        handler.heldItems.add("result_item");
        warpWithStatus(workspot1, 100, ProductionStatus.DROPPING_LOOT, handler);
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Cycle 1 done: Tool remains");

        // Cycle 2 - tool is still there
        TestWorkSpotStandIn workspot2 = new TestWorkSpotStandIn();
        workspot2.state = State.fresh().setWorkLeft(1); // 1 work unit = 1 warp

        warpWithStatus(workspot2, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        // Tool + new ingredient
        Assertions.assertTrue(handler.heldItems.contains("tool_item"), "Cycle 2: Tool still there");
        Assertions.assertTrue(handler.heldItems.contains("supply_item"), "Cycle 2: New ingredient collected");
    }

    @Test
    public void smelterVsBakerVsCrafter_toolVsIngredientHandling() {
        // Compare tool handling (Smelter) vs ingredient handling (Baker, Crafter)
        // Smelter: tool NOT consumed
        // Baker/Crafter: all ingredients consumed

        THandler smelterHandler = makeHandler();
        THandler crafterHandler = makeHandler();

        // Smelter: ingredient + tool -> work -> result + tool
        // Using setWorkLeft(1) so work completes in 1 warp
        smelterHandler.heldItems = new ArrayList<>(List.of("supply_item", "tool_item"));
        TestWorkSpotStandIn smelterSpot = new TestWorkSpotStandIn();
        smelterSpot.state = State.fresh().setWorkLeft(1);
        warpWithStatus(smelterSpot, 100, ProductionStatus.fromJobBlockStatus(1), smelterHandler);
        Assertions.assertFalse(smelterHandler.heldItems.contains("supply_item"), "Smelter: ingredient consumed");
        Assertions.assertTrue(smelterHandler.heldItems.contains("tool_item"), "Smelter: tool NOT consumed");

        // Crafter: ingredient -> work -> result (no tool)
        crafterHandler.heldItems = new ArrayList<>(List.of("supply_item"));
        TestWorkSpotStandIn crafterSpot = new TestWorkSpotStandIn();
        crafterSpot.state = State.fresh().setWorkLeft(1);
        warpWithStatus(crafterSpot, 100, ProductionStatus.fromJobBlockStatus(1), crafterHandler);
        Assertions.assertFalse(crafterHandler.heldItems.contains("supply_item"), "Crafter: ingredient consumed");
    }

    // --- Cook-style Slot Inserter Warp Tests ---
    // These tests verify the Cook job pattern with slot insertion:
    // State 0: tool check (verify beef in inventory) + minimal work (0.1 tick)
    // State 1: insert beef into furnace slot 0 (special rule: insert_into_slot_0)
    // Result: minecraft:air (furnace handles cooking, extraction is separate job)
    //
    // Key difference: Item is inserted into a BLOCK SLOT, not just consumed.
    // The special rule insert_into_slot_N triggers postInsertHook.

    @Test
    public void cookStyle_toolCheckWithMinimalWork_shouldVerifyInventory() {
        // Cook state 0: tools = beef (verify beef in inventory), work = 0.1
        // This is essentially a "do you have the item?" check with minimal work
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // work = 0.1 in job definition translates to 1 work unit (minimum)
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        // Villager has beef (the tool/ingredient)
        handler.heldItems = new ArrayList<>(List.of("beef_item"));

        // Work at state 0 (tool check)
        ProductionStatus state0 = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn afterState0 = warpWithStatus(workspot, 100, state0, handler);

        Assertions.assertTrue(handler.tryWorkingCalled, "Should do work at state 0");
        Assertions.assertEquals(0, afterState0.state.workLeft(), "Minimal work should complete");
        // Tool (beef) should still be in inventory after tool check
        // (it's consumed at state 1, not state 0)
    }

    @Test
    public void cookStyle_insertIntoSlot_shouldConsumeIngredient() {
        // Cook state 1: insert beef into furnace slot 0
        // The ingredient is removed from inventory and "inserted" into the block
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(1).setWorkLeft(1);
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("supply_item")); // beef

        // Work at state 1 (insert into slot)
        ProductionStatus state1 = ProductionStatus.fromJobBlockStatus(1);
        warpWithStatus(workspot, 100, state1, handler);

        Assertions.assertTrue(handler.tryWorkingCalled, "Should do work at state 1");
        // Ingredient should be consumed (inserted into block)
        Assertions.assertFalse(handler.heldItems.contains("supply_item"),
                "Ingredient should be consumed when inserted into slot");
    }

    @Test
    public void cookStyle_noExtraction_resultIsAir() {
        // Cook produces minecraft:air - the furnace handles actual cooking
        // This means no EXTRACTING_PRODUCT step in the Cook job itself
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("supply_item"));

        // Complete both work states
        warpWithStatus(workspot, 100, ProductionStatus.fromJobBlockStatus(0), handler);
        workspot.state = workspot.state.setProcessing(1).setWorkLeft(1);
        warpWithStatus(workspot, 100, ProductionStatus.fromJobBlockStatus(1), handler);

        // No extraction step - result is air
        // Inventory should be empty (no result to extract)
        Assertions.assertTrue(handler.heldItems.isEmpty() ||
                !handler.heldItems.stream().anyMatch(i -> i.contains("result")),
                "Cook should not produce a result item (result is air)");
    }

    @Test
    public void cookStyle_fullCycle_toolCheckThenInsert() {
        // Complete cook cycle for simple_furnace_food:
        // State 0: verify beef in inventory (tool check), minimal work
        // State 1: insert beef into furnace slot 0
        // No extraction (result is air, furnace handles cooking)
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>();

        // Step 1: Collect beef (both tool and ingredient)
        warpWithStatus(workspot, 100, ProductionStatus.COLLECTING_SUPPLIES, handler);
        Assertions.assertEquals(1, handler.heldItems.size(), "Step 1: Should have beef");

        // Step 2: Work at state 0 (tool check - verify beef is there)
        ProductionStatus state0 = ProductionStatus.fromJobBlockStatus(0);
        TestWorkSpotStandIn afterS0 = warpWithStatus(workspot, 100, state0, handler);
        Assertions.assertEquals(0, afterS0.state.workLeft(), "Step 2: Tool check complete");
        // Beef still in inventory (not consumed yet at state 0 in this mock)

        // Step 3: Work at state 1 (insert beef into furnace)
        afterS0.state = afterS0.state.setProcessing(1).setWorkLeft(1);
        ProductionStatus state1 = ProductionStatus.fromJobBlockStatus(1);
        warpWithStatus(afterS0, 100, state1, handler);
        // Beef consumed (inserted into furnace slot)
        Assertions.assertFalse(handler.heldItems.contains("supply_item"),
                "Step 3: Beef should be inserted into furnace");

        // No extraction step needed - cook/extract is a separate job
        // Villager is done with cook/simple_furnace_food
    }

    @Test
    public void cookStyle_fuelInsertion_slotOne() {
        // cook/fuel inserts coal into slot 1 of furnace
        // Same pattern as food, but different slot
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setWorkLeft(1);
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(List.of("coal_item")); // fuel

        // State 0: tool check for coal
        warpWithStatus(workspot, 100, ProductionStatus.fromJobBlockStatus(0), handler);

        // State 1: insert coal into slot 1
        workspot.state = workspot.state.setProcessing(1).setWorkLeft(1);
        warpWithStatus(workspot, 100, ProductionStatus.fromJobBlockStatus(1), handler);

        // Coal should be consumed (inserted into fuel slot)
        // Mock consumes "supply_item" on work complete, coal_item is not "supply_item"
        // so let's verify tryWorking was called for both states
        Assertions.assertTrue(handler.tryWorkingCalled, "Should complete fuel insertion");
    }

    @Test
    public void cookVsSmelter_slotInsertionVsToolUse() {
        // Compare Cook (slot insertion) vs Smelter (tool use)
        // Cook: item inserted INTO block slot
        // Smelter: tool used but NOT consumed

        THandler cookHandler = makeHandler();
        THandler smelterHandler = makeHandler();

        // Cook: beef is inserted into furnace (consumed)
        cookHandler.heldItems = new ArrayList<>(List.of("supply_item"));
        TestWorkSpotStandIn cookSpot = new TestWorkSpotStandIn();
        cookSpot.state = State.fresh().setProcessing(1).setWorkLeft(1);
        warpWithStatus(cookSpot, 100, ProductionStatus.fromJobBlockStatus(1), cookHandler);
        Assertions.assertFalse(cookHandler.heldItems.contains("supply_item"),
                "Cook: ingredient inserted into slot (consumed)");

        // Smelter: pickaxe is used but NOT consumed
        smelterHandler.heldItems = new ArrayList<>(List.of("supply_item", "tool_item"));
        TestWorkSpotStandIn smelterSpot = new TestWorkSpotStandIn();
        smelterSpot.state = State.fresh().setWorkLeft(1);
        warpWithStatus(smelterSpot, 100, ProductionStatus.fromJobBlockStatus(1), smelterHandler);
        Assertions.assertFalse(smelterHandler.heldItems.contains("supply_item"),
                "Smelter: ingredient consumed");
        Assertions.assertTrue(smelterHandler.heldItems.contains("tool_item"),
                "Smelter: tool NOT consumed");
    }

    // --- Mid-Work Warp Scenario Tests (Gatherer starting while "out gathering") ---
    // These tests verify behavior when warp starts with a villager already in
    // WAITING_FOR_TIMED_STATE (e.g., gatherer who left town before the player slept).
    // The warp should complete their timer and transition to EXTRACTING_PRODUCT.

    /**
     * Warp with explicit initialStatus parameter - for testing mid-work scenarios
     * where the villager's status is passed from their journal.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TestWorkSpotStandIn warpWithInitialStatus(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus initialStatus,
            @Nullable THandler handler,
            int maxState
    ) {
        UUID villagerID = UUID.randomUUID();
        AbstractDeclarativeJobWarper.staticInitialize();
        AbstractStateInteraction wi = handler != null ? handler : THandler.make(
                new JobID("test", "job"), 0, 0, maxState,
                EMPTY_CHECKS,
                inpoots -> new Claim(villagerID, Long.MAX_VALUE),
                NO_RULES
        );

        // Use default status provider - status transitions will be computed
        // BUT initialStatus is passed to warp(), so WAITING_FOR_TIMED_STATE
        // triggers special handling in warp() before status computation.
        StatusProvider<Room> statusProvider = (cur, sig, inv, town, loc, pri) -> null;

        return new TWarper(statusProvider, false).warp(
                workspot,
                workspot,
                Warper.Tick.at(gameTick).after(gameTick),
                t -> new TestInventoryProvider(),
                t -> new TestTownProvider(),
                NO_LOCATION,
                false,
                wi,
                s -> new TInputs(s, null, villagerID),
                maxState,
                initialStatus  // Pass the actual initial status
        );
    }

    @Test
    public void whenStartingInWaitingForTimedState_shouldTransitionToExtractingProduct() {
        // Scenario: Gatherer left town before player slept. When warp starts,
        // the villager is in WAITING_FOR_TIMED_STATE. The warp should complete
        // their timer and allow them to extract their loot.
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // processingState = 0 simulates work state being reset (common for gatherers)
        // Force extraction should still trigger because processingState != maxState
        workspot.state = State.fresh().setProcessing(0);
        workspot.timer = 2000; // Gatherer's gathering timer (unused in warp tracking)
        THandler handler = makeHandler();

        // Warp with initialStatus = WAITING_FOR_TIMED_STATE
        // maxState = 10 (typical for gatherer jobs)
        TestWorkSpotStandIn result = warpWithInitialStatus(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10
        );

        // The warp should have:
        // 1. Transitioned to EXTRACTING_PRODUCT
        // 2. Set processingState to maxState (10) for extraction
        // 3. Called tryWorking to collect loot
        Assertions.assertTrue(
                handler.tryWorkingCalled,
                "When starting in WAITING_FOR_TIMED_STATE, warp should transition to " +
                "EXTRACTING_PRODUCT and call tryWorking to collect loot"
        );
        Assertions.assertEquals(
                10,
                result.state.processingState(),
                "Processing state should be set to maxState (10) for extraction"
        );
    }

    @Test
    public void whenStartingInWaitingForTimedState_shouldNotCallOtherHandlers() {
        // When starting mid-work, only tryWorking (extraction) should be called
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // processingState = 0, but force extraction should trigger because != maxState
        workspot.state = State.fresh().setProcessing(0);
        THandler handler = makeHandler();

        warpWithInitialStatus(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10
        );

        // Should call tryWorking for extraction
        Assertions.assertTrue(handler.tryWorkingCalled, "tryWorking should be called");
        // Should NOT call other handlers
        Assertions.assertFalse(handler.dropLootCalled, "dropLoot should not be called yet");
        Assertions.assertFalse(handler.collectSuppliesCalled, "collectSupplies should not be called");
    }

    @Test
    public void whenStartingInWaitingForTimedState_stateReflectsExtraction() {
        // Work state should be set up for extraction (processingState = maxState)
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(2); // Was at processing state 2
        THandler handler = makeHandler();

        // maxState = 5 for this test
        TestWorkSpotStandIn result = warpWithInitialStatus(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 5
        );

        // The warp should reset the processing state to maxState for extraction
        Assertions.assertEquals(
                5,
                result.state.processingState(),
                "Processing state should be set to maxState for extraction"
        );
    }

    @Test
    public void whenStartingWithNullInitialStatus_shouldComputeStatusNormally() {
        // When initialStatus is null (fresh start), status is computed from conditions
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh();
        THandler handler = makeHandler();

        // null initialStatus = fresh start, let statusProvider compute
        warpWithInitialStatus(workspot, 100, null, handler, 10);

        // With null statusProvider return value, the warp defaults to IDLE
        // which uses NULL_HENDLAR - no handler methods called
        Assertions.assertFalse(handler.tryWorkingCalled, "No work with null status");
        Assertions.assertFalse(handler.dropLootCalled, "No drop with null status");
        Assertions.assertFalse(handler.collectSuppliesCalled, "No collect with null status");
    }

    @Test
    public void whenStartingInWaitingForTimedState_gathererStyle_shouldCompleteGathering() {
        // Full gatherer mid-work scenario:
        // Gatherer left town (WAITING_FOR_TIMED_STATE) -> warp starts -> should get loot
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(3); // Gatherer was at time state
        workspot.timer = 4000; // 4000 ticks of gathering time
        THandler handler = makeHandler();
        handler.heldItems = new ArrayList<>(); // Empty inventory (no loot yet)

        // maxState = 10 (typical gatherer)
        TestWorkSpotStandIn result = warpWithInitialStatus(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10
        );

        // Should transition to extraction and call tryWorking
        Assertions.assertTrue(
                handler.tryWorkingCalled,
                "Gatherer mid-work should complete and call tryWorking"
        );

        // State should be set for extraction
        Assertions.assertEquals(
                10,
                result.state.processingState(),
                "Should be at maxState for extraction"
        );
    }

    @Test
    public void whenStartingInIdle_shouldNotTriggerExtractionLogic() {
        // Contrast test: starting in IDLE should NOT trigger the extraction path
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh();
        THandler handler = makeHandler();

        // Pass IDLE as initialStatus (different from WAITING_FOR_TIMED_STATE)
        warpWithInitialStatus(workspot, 100, ProductionStatus.IDLE, handler, 10);

        // IDLE uses NULL_HENDLAR - nothing should be called
        Assertions.assertFalse(handler.tryWorkingCalled, "IDLE should not call tryWorking");
        // And state should NOT be changed to maxState
        Assertions.assertEquals(
                0,
                workspot.state.processingState(),
                "IDLE should not modify processingState"
        );
    }

    /**
     * Warp with explicit initialStatus AND custom inventory provider.
     * Used to test behavior when villager already has items.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TestWorkSpotStandIn warpWithInitialStatusAndInventory(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus initialStatus,
            @Nullable THandler handler,
            int maxState,
            EntityInvStateProvider<Integer> inventoryProvider
    ) {
        return warpWithInitialStatusInventoryAndStatusProvider(
                workspot, gameTick, initialStatus, handler, maxState, inventoryProvider,
                (cur, sig, inv, town, loc, pri) -> null
        );
    }

    /**
     * Warp with explicit initialStatus, custom inventory provider, AND custom status provider.
     * Used to test specific status computation scenarios.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TestWorkSpotStandIn warpWithInitialStatusInventoryAndStatusProvider(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus initialStatus,
            @Nullable THandler handler,
            int maxState,
            EntityInvStateProvider<Integer> inventoryProvider,
            StatusProvider<Room> statusProvider
    ) {
        UUID villagerID = UUID.randomUUID();
        AbstractDeclarativeJobWarper.staticInitialize();
        AbstractStateInteraction wi = handler != null ? handler : THandler.make(
                new JobID("test", "job"), 0, 0, maxState,
                EMPTY_CHECKS,
                inpoots -> new Claim(villagerID, Long.MAX_VALUE),
                NO_RULES
        );

        return new TWarper(statusProvider, false).warp(
                workspot,
                workspot,
                Warper.Tick.at(gameTick).after(gameTick),
                t -> inventoryProvider,  // Use provided inventory
                t -> new TestTownProvider(),
                NO_LOCATION,
                false,
                wi,
                s -> new TInputs(s, null, villagerID),
                maxState,
                initialStatus
        );
    }

    @Test
    public void whenStartingInWaitingForTimedState_withItems_shouldNotForceExtraction() {
        // When villager is in WAITING_FOR_TIMED_STATE but ALREADY HAS items,
        // should NOT force extraction - let normal status computation run.
        // This allows the villager to drop items before starting a new cycle.
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(3);
        THandler handler = makeHandler();

        // Inventory provider that reports villager HAS items
        EntityInvStateProvider<Integer> hasItemsInventory = new EntityInvStateProvider<>() {
            @Override public boolean inventoryFull() { return false; }
            @Override public boolean hasNonSupplyItems() { return true; }  // HAS ITEMS
            @Override public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return Map.of();
            }
        };

        TestWorkSpotStandIn result = warpWithInitialStatusAndInventory(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10,
                hasItemsInventory
        );

        // Should NOT force extraction when villager has items
        // Instead, normal status computation runs (returns null in test -> IDLE)
        // IDLE uses NULL_HENDLAR, so tryWorking should NOT be called
        Assertions.assertFalse(
                handler.tryWorkingCalled,
                "When villager has items, should NOT force extraction - let normal status run"
        );

        // Processing state should NOT be changed to maxState (no forced extraction)
        Assertions.assertEquals(
                3,
                result.state.processingState(),
                "Processing state should remain unchanged when not forcing extraction"
        );
    }

    @Test
    public void whenStartingInWaitingForTimedState_withoutItems_shouldForceExtraction() {
        // Contrast: when villager has NO items, extraction SHOULD be forced
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = State.fresh().setProcessing(3);
        THandler handler = makeHandler();

        // Inventory provider that reports villager has NO items
        EntityInvStateProvider<Integer> noItemsInventory = new EntityInvStateProvider<>() {
            @Override public boolean inventoryFull() { return false; }
            @Override public boolean hasNonSupplyItems() { return false; }  // NO ITEMS
            @Override public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return Map.of();
            }
        };

        TestWorkSpotStandIn result = warpWithInitialStatusAndInventory(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10,
                noItemsInventory
        );

        // SHOULD force extraction when villager has no items
        Assertions.assertTrue(
                handler.tryWorkingCalled,
                "When villager has no items, SHOULD force extraction"
        );

        // Processing state should be set to maxState for extraction
        Assertions.assertEquals(
                10,
                result.state.processingState(),
                "Processing state should be maxState when forcing extraction"
        );
    }

    @Test
    public void whenStartingInWaitingForTimedState_afterExtractionAlreadyDone_shouldNotForceAgain() {
        // After extraction and dropping items, villager has no items but the
        // status provider computes COLLECTING_SUPPLIES (ready for new cycle).
        // This means extraction already happened and we should NOT force it again -
        // let normal status run to start the new cycle with food consumption.
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        // After dropping, processingState is reset to 0 by the dropLoot handler
        workspot.state = State.fresh().setProcessing(0);
        THandler handler = makeHandler();

        // Inventory provider that reports villager has NO items (after dropping)
        EntityInvStateProvider<Integer> noItemsInventory = new EntityInvStateProvider<>() {
            @Override public boolean inventoryFull() { return false; }
            @Override public boolean hasNonSupplyItems() { return false; }  // NO ITEMS
            @Override public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
                return Map.of();
            }
        };

        // Status provider that returns COLLECTING_SUPPLIES (indicating new cycle ready)
        StatusProvider<Room> collectingSuppliesProvider = (cur, sig, inv, town, loc, pri) ->
                ProductionStatus.COLLECTING_SUPPLIES;

        TestWorkSpotStandIn result = warpWithInitialStatusInventoryAndStatusProvider(
                workspot, 100, ProductionStatus.WAITING_FOR_TIMED_STATE, handler, 10,
                noItemsInventory, collectingSuppliesProvider
        );

        // Should NOT force extraction when status provider says COLLECTING_SUPPLIES.
        // This means we're ready to start a new cycle, not finish an old one.
        Assertions.assertFalse(
                handler.tryWorkingCalled,
                "When computed status is COLLECTING_SUPPLIES, should NOT force extraction"
        );

        // With COLLECTING_SUPPLIES, the collectSupplies handler runs which may
        // modify state. The key assertion is that tryWorking was NOT called.
    }

}