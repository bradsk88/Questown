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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

class JobLogicTest {

    static Position ARBITRARY_WORKSPOT_POS = new Position(0, 0);
    static WorkSpot<Integer, Position> ARBITRARY_WORKSPOT = new WorkSpot<>(
            ARBITRARY_WORKSPOT_POS,
            0,
            1,
            ARBITRARY_WORKSPOT_POS
    );

    static int STATE_NEED_WIDGET = 0;

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
            ImmutableMap.of(),
            ImmutableMap.of(),
            "test report"
    );

    private static class TestLogicWorld implements JobLogic.JLWorld<Void, Position> {

        private WorkSpot<Integer, Position> workspot;
        MapBackedWSC states = new MapBackedWSC();
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory = TestInventory.sized(3);

        private TestWorldInteraction wi = TestWorldInteraction.forDefinition(
                JobLogicTest.DEFINITION, inventory, states, () -> null
        );
        private Map<Integer, Collection<WorkSpot<Integer, Position>>> allWorkSpots = ImmutableMap.of();

        @Override
        public AbstractWorldInteraction<Void, Position, ?, ?, ?> getHandle() {
            return wi;
        }

        @Override
        public void changeJob(JobID id) {
        }

        @Override
        public WorkSpot<Integer, Position> getWorkSpot() {
            return this.workspot;
        }

        @Override
        public void clearWorkSpot(String reason) {
            this.workspot = null;
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
        public void tryDropLoot() {

        }

        @Override
        public void tryGetSupplies() {

        }

        @Override
        public void seekFallbackWork() {

        }

        @Override
        public Map<Integer, Collection<WorkSpot<Integer, Position>>> listAllWorkSpots() {
            return allWorkSpots;
        }

        @Override
        public void setLookTarget(Position position) {

        }

        @Override
        public void registerHeldItemsAsFoundLoot() {

        }
    }

    @Test
    void tick_ShouldShouldWorkIfIngredientsNeededAndHad() {
        JobLogic<Void, Position> logic = new JobLogic<>();
        TestLogicWorld world = new TestLogicWorld();

        world.workspot = ARBITRARY_WORKSPOT;
        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        logic.tick(
                null,
                () -> ProductionStatus.fromJobBlockStatus(0),
                new JobID("test", "tester"),
                true,
                false,
                false,
                ExpirationRules.never(),
                DEFINITION.maxState(),
                world
        );

        Assertions.assertEquals(
                State.fresh().incrProcessing(),
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
    void tick_ShouldCollectSuppliesIfTargetExists(boolean entityInJobSite, boolean expectCollect) {
        JobLogic<Void, Position> logic = new JobLogic<>();

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld() {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };

        world.allWorkSpots = ImmutableMap.of(
                STATE_NEED_WIDGET, ImmutableList.of(ARBITRARY_WORKSPOT)
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        world.inventory.set(0, new GathererJournalTest.TestItem("widget"));

        logic.tick(
                null,
                () -> ProductionStatus.COLLECTING_SUPPLIES,
                new JobID("test", "tester"),
                entityInJobSite,
                false,
                false,
                ExpirationRules.never(),
                DEFINITION.maxState(),
                world
        );

        Assertions.assertNull(
                world.states.getJobBlockState(ARBITRARY_WORKSPOT_POS)
        );

        Assertions.assertEquals(expectCollect, triedToGetSupplies.get());
    }

    @Test()
    void tick_shouldGiveUpAfterInitialTicks_IfNeverInserted() {
        JobLogic<Void, Position> logic = new JobLogic<>();

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld() {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };

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
                ExpirationRules.never().withInitialNoSupplyTickLimit(1).withNoSupplyTickLimit(2),
                DEFINITION.maxState(),
                world
        );

        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertTrue(logic.isGrabbingInsertedSupplies());
    }
    @Test()
    void tick_shouldGiveUpAfterInitialTicks_IfInsertedAtLeastOnce() {
        JobLogic<Void, Position> logic = new JobLogic<>();

        final AtomicBoolean triedToGetSupplies = new AtomicBoolean(false);

        TestLogicWorld world = new TestLogicWorld() {
            @Override
            public void tryGetSupplies() {
                super.tryGetSupplies();
                triedToGetSupplies.set(true);
            }
        };

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
                ExpirationRules.never().withInitialNoSupplyTickLimit(1).withNoSupplyTickLimit(2),
                DEFINITION.maxState(),
                world
        );

        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertFalse(logic.isGrabbingInsertedSupplies());
        tickFn.run();
        Assertions.assertTrue(logic.isGrabbingInsertedSupplies());
    }


    static Stream<Arguments> provideNotProductionStatuses() {
        return Stream.of(
                Arguments.of(ProductionStatus.COLLECTING_SUPPLIES)
        );
    }

    @ParameterizedTest
    @MethodSource("provideNotProductionStatuses")
    void tick_shouldClearWorkSpotIfStatusIsNotProduction(ProductionStatus status) {
        List<WorkSpot<Integer, Position>> workSpotHistory = new ArrayList<>();

        JobLogic<Void, Position> logic = new JobLogic<>() {
            @Override
            protected void setWorkSpot(WorkSpot<Integer, Position> spot) {
                super.setWorkSpot(spot);
                workSpotHistory.add(spot);
            }
        };

        TestLogicWorld world = new TestLogicWorld();
        WorkSpot<Integer, Position> arbitraryWorkSpot = new WorkSpot<>(new Position(1, 2), 1, 1, new Position(1, 2));
        world.workspot = arbitraryWorkSpot;

        logic.tick(
                null,
                () -> ProductionStatus.NO_SUPPLIES,
                new JobID("test", "tester"),
                true,
                false,
                new Random().nextBoolean(),
                ExpirationRules.never(),
                DEFINITION.maxState(),
                world
        );

        Assertions.assertNull(logic.workSpot());
        Assertions.assertEquals(2, workSpotHistory.size());
        Assertions.assertNotNull(workSpotHistory.get(0));
        Assertions.assertNull(workSpotHistory.get(1));
    }
}