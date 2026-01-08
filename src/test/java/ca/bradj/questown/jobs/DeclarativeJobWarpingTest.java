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

class DeclarativeJobWarpingTest {

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TestWorkSpotStandIn warpWithStatus(
            TestWorkSpotStandIn workspot,
            int gameTick,
            @Nullable ProductionStatus status,
            @Nullable THandler handler
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

        return new TWarper(statusProvider).warp(
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
}