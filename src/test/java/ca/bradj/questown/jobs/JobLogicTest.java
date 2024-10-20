package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.blacksmith.MapBackedWSC;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.TestWorldInteraction;
import ca.bradj.questown.jobs.declarative.ValidatedInventoryHandle;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

class JobLogicTest {

    static Position ARBITRARY_WORKSPOT_POS = new Position(0, 0);
    static WorkPosition<Position> ARBITRARY_WORKSPOT = new WorkPosition<>(
            ARBITRARY_WORKSPOT_POS,
            ARBITRARY_WORKSPOT_POS
    );

    static int STATE_NEED_WIDGET = 0;
    static int STATE_NEED_WORK = 1;

    static JobDefinition DEFINITION = new JobDefinition(
            new JobID("tester", "test"),
            3,
            ImmutableMap.of(
                    STATE_NEED_WIDGET, "widget"
            ),
            ImmutableMap.of(
                    STATE_NEED_WIDGET, 1
            ),
            ImmutableMap.of(),
            ImmutableMap.of(
                    STATE_NEED_WORK, 1
            ),
            ImmutableMap.of(),
            "gold nugget"
    );

    private static final JobLogic.JobDetails DEFAULT_DETAILS = new JobLogic.JobDetails(
            DEFINITION.maxState(),
            DEFINITION.workRequiredAtStates().get(0),
            0
    );

    private static class TestLogicWorld implements JobLogic.JLWorld<Void, Boolean, Position> {

        private WorkPosition<Position> workspot;
        MapBackedWSC states = new MapBackedWSC();
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory = TestInventory.sized(3);

        private TestWorldInteraction wi;
        private Map<Integer, Collection<WorkPosition<Position>>> allWorkSpots = ImmutableMap.of();

        private TestLogicWorld(JobDefinition definition) {
            this.wi = TestWorldInteraction.forDefinition(
                    definition, inventory, states, () -> null
            );
        }

        @Override
        public AbstractWorldInteraction<Void, Position, ?, ?, Boolean> getHandle() {
            return wi;
        }

        /**
         * @deprecated Should only be overloaded with strong reason
         */
        @Override
        public void changeJob(JobID id) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Should only be overloaded with strong reason
         */
        @Override
        public void changeToNextJob() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setWorkLeftAtFreshState(int work) {
            State cur = states.getJobBlockState(ARBITRARY_WORKSPOT_POS);
            if (cur == null) {
                cur = State.fresh();
            }
            this.states.setJobBlockState(ARBITRARY_WORKSPOT_POS, cur.setWorkLeft(work));
            return true;
        }

        @Override
        public void clearInsertedSupplies() {
        }

        @Override
        public WorkPosition<Position> getWorkSpot() {
            return this.workspot;
        }

        @Override
        public boolean tryGrabbingInsertedSupplies() {
            return false;
        }

        @Override
        public boolean canDropLoot() {
            return false;
        }

        @Override
        public boolean tryDropLoot() {

            return false;
        }

        @Override
        public void tryGetSupplies() {

        }

        @Override
        public void seekFallbackWork() {

        }

        @Override
        public Map<Integer, Collection<WorkPosition<Position>>> listAllWorkSpots() {
            return allWorkSpots;
        }

        @Override
        public void setLookTarget(Position position) {

        }

        @Override
        public void registerHeldItemsAsFoundLoot() {

        }
    }

    private static class TestJobLogic extends JobLogic<Void, Boolean, Position> {
        private final JobDefinition definition;
        private final TestLogicWorld world;

        public TestJobLogic(
                TestLogicWorld world,
                JobDefinition def
        ) {
            setup(DEFAULT_DETAILS.workRequiredAtFirstState(), world);
            this.definition = def;
            this.world = world;
        }

        public void defaultTick(
                ProductionStatus status,
                boolean entityInJobSite,
                Function<Position, Integer> blockState
        ) {
            tick(
                    null,
                    () -> status,
                    definition.jobId(),
                    entityInJobSite,
                    false,
                    new Random().nextBoolean(),
                    false,
                    ExpirationRules.never(),
                    new JobLogic.JobDetails(definition.maxState(), definition.workRequiredAtStates().get(0), 0),
                    world,
                    (a, b) -> blockState.apply(b)
            );
        }
    }

    @Test
    void tick_ShouldShouldWorkIfIngredientsNeededAndHad() {
        TestLogicWorld world = new TestLogicWorld(DEFINITION);
        TestJobLogic logic = new TestJobLogic(world, DEFINITION);

        world.workspot = ARBITRARY_WORKSPOT;
        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        logic.defaultTick(ProductionStatus.fromJobBlockStatus(0), true, p -> 0);

        Integer nextWork = DEFINITION.workRequiredAtStates().get(STATE_NEED_WORK);
        Assertions.assertEquals(
                State.fresh().incrProcessing().setWorkLeft(nextWork),
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );
    }


    static Stream<Arguments> provideCollectSuppliesArgs() {
        return Stream.of(
                Arguments.of(true, true), // Collect supplies if in job site
                Arguments.of(false, true) // Collect supplies if out of job site
        );
    }

    @ParameterizedTest
    @MethodSource("provideCollectSuppliesArgs")
    void tick_ShouldCollectSuppliesIfTargetExists(
            boolean entityInJobSite,
            boolean expectCollect
    ) {

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld(DEFINITION) {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };
        TestJobLogic logic = new TestJobLogic(world, DEFINITION);

        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        logic.defaultTick(ProductionStatus.COLLECTING_SUPPLIES, entityInJobSite, p -> 0);

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        Assertions.assertEquals(expectCollect, triedToGetSupplies.get());
    }

    @Test()
    void tick_shouldGiveUpAfterInitialTicks_IfNeverInserted() {

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld(DEFINITION) {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };
        TestJobLogic logic = new TestJobLogic(world, DEFINITION);

        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        Runnable tickFn = () -> logic.tick(
                null,
                () -> ProductionStatus.NO_SUPPLIES,
                new JobID("test", "tester"),
                true,
                false,
                false,
                false,
                ExpirationRules.never().withInitialNoSupplyTickLimit(1).withNoSupplyTickLimit(2),
                DEFAULT_DETAILS,
                world,
                (a, b) -> 0
        );

        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertTrue(logic.isGrabbingInsertedSupplies());
    }

    @Test()
    void tick_shouldGiveUpAfterInitialTicks_IfInsertedAtLeastOnce() {

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld(DEFINITION) {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };
        JobLogic<Void, Boolean, Position> logic = new TestJobLogic(world, DEFINITION);

        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        Runnable tickFn = () -> logic.tick(
                null,
                () -> ProductionStatus.NO_SUPPLIES,
                new JobID("test", "tester"),
                true,
                false,
                true,
                false,
                ExpirationRules.never().withInitialNoSupplyTickLimit(1).withNoSupplyTickLimit(2),
                DEFAULT_DETAILS,
                world,
                (a, b) -> 0
        );

        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run(); // Try finding supplies
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run(); // Try finding supplies again
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run(); // This tick should give up
        Assertions.assertTrue(logic.isGrabbingInsertedSupplies());
    }


    static Stream<Arguments> provideNotProductionStatuses() {
        return Stream.of(
                Arguments.of(ProductionStatus.COLLECTING_SUPPLIES)
        );
    }

    @Disabled("Doesn't seem to be needed anymore. Remove if play testing is successful")
    @ParameterizedTest
    @MethodSource("provideNotProductionStatuses")
    void tick_shouldClearWorkSpotIfStatusIsNotProduction(ProductionStatus status) {
        List<WorkPosition<Position>> workSpotHistory = new ArrayList<>();

        TestLogicWorld world = new TestLogicWorld(DEFINITION);
        TestJobLogic logic = new TestJobLogic(world, DEFINITION) {
            @Override
            protected void setWorkSpot(WorkPosition<Position> spot) {
                super.setWorkSpot(spot);
                workSpotHistory.add(spot);
            }
        };

        WorkPosition<Position> arbitraryWorkSpot = new WorkPosition<>(new Position(1, 2), new Position(1, 2));
        world.workspot = arbitraryWorkSpot;

        logic.defaultTick(ProductionStatus.NO_SUPPLIES, true, p -> 0);

        Assertions.assertNull(logic.workSpot());
        Assertions.assertEquals(2, workSpotHistory.size());
        Assertions.assertNotNull(workSpotHistory.get(0));
        Assertions.assertNull(workSpotHistory.get(1));
    }

    @Test
    void tick_shouldUpdateWorkSpotActionAfterFinishingWork() {
        List<WorkPosition<Position>> workSpotHistory = new ArrayList<>();

        TestLogicWorld world = new TestLogicWorld(DEFINITION);
        TestJobLogic logic = new TestJobLogic(world, DEFINITION) {
            @Override
            protected void setWorkSpot(WorkPosition<Position> spot) {
                super.setWorkSpot(spot);
                workSpotHistory.add(spot);
            }
        };

        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        logic.defaultTick(ProductionStatus.fromJobBlockStatus(0), true, p -> 0);

        Assertions.assertEquals(1, workSpotHistory.size());
        Assertions.assertNotNull(workSpotHistory.get(0));
        Assertions.assertEquals(1, world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS).processingState());

    }

    @Test
    void tick_shouldChangeJob_AfterWorkingThroughAllSteps() {
        AtomicBoolean changedJob = new AtomicBoolean();

        TestLogicWorld world = new TestLogicWorld(DEFINITION) {
            @Override
            public void changeToNextJob() {
                changedJob.set(true);
            }
        };
        TestJobLogic logic = new TestJobLogic(world, DEFINITION);

        Consumer<Integer> ticker = (state) -> {
            world.allWorkSpots = ImmutableMap.of(
                    state, ImmutableList.of(ARBITRARY_WORKSPOT)
            );

            logic.defaultTick(
                    ProductionStatus.fromJobBlockStatus(state),
                    true,
                    (Position b) -> world.states.getJobBlockState(b).processingState()
            );
        };

        // First, insert the ingredient
        ticker.accept(STATE_NEED_WIDGET);
        Assertions.assertFalse(changedJob.get());

        // Then, do some work
        ticker.accept(STATE_NEED_WORK);
        Assertions.assertFalse(changedJob.get());

        // Then, extract the product
        world.allWorkSpots = ImmutableMap.of(
                DEFINITION.maxState(), ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        ticker.accept(DEFINITION.maxState());
        Assertions.assertFalse(changedJob.get());

        // Then, change job
        ticker.accept(DEFINITION.maxState());
        Assertions.assertTrue(changedJob.get());
    }

    @Test
    void tick_shouldImmediatelySetJobState_IfFirstStepContainsWorkOnly() {


        JobDefinition definition = new JobDefinition(
                new JobID("tester", "test"),
                3,
                ImmutableMap.of(
                        // No items required
                ),
                ImmutableMap.of(
                        // No items required
                ),
                ImmutableMap.of(
                        // No tools required
                ),
                ImmutableMap.of(
                        0, 1 // 1 work required
                ),
                ImmutableMap.of(
                        // No timers
                ),
                "gold nugget"
        );

        TestLogicWorld world = new TestLogicWorld(definition);
        JobLogic<Void, Boolean, Position> logic = new JobLogic<>();

        if (world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS) != null) {
            throw new IllegalStateException("Should have no state until ticked");
        }

        world.allWorkSpots = ImmutableMap.of(
                0, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        logic.tick(
                null,
                () -> ProductionStatus.fromJobBlockStatus(0),
                definition.jobId(),
                true,
                false,
                false,
                false,
                ExpirationRules.never(),
                new JobLogic.JobDetails(definition.maxState(), definition.workRequiredAtStates().get(0), 0),
                world,
                (a, b) -> world.states.getJobBlockState(b).processingState()
        );

        Assertions.assertTrue(world.states.getJobBlockState(ARBITRARY_WORKSPOT.jobBlock()).hasWorkLeft());
    }

    @Disabled("Not implemented")
    @Test
    void tick_shouldNotSetJobState_IfFirstStepRequiresItemsOrWork() {
        Assertions.fail("Not implemented");
    }
}
