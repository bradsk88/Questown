package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class DeclarativeJobWarpingTest {

    private Function<TestWorkSpotStandIn, EntityInvStateProvider<Integer>> inventory = town -> new EntityInvStateProvider<>() {

        @Override
        public boolean inventoryFull() {
            return false;
        }

        @Override
        public boolean hasNonSupplyItems() {
            return false;
        }

        @Override
        public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
            return Map.of();
        }
    };
    private Function<TestWorkSpotStandIn, JobTownProvider<Room>> town = town -> new JobTownProvider<>() {

        @Override
        public boolean hasSupplies() {
            return true;
        }

        @Override
        public boolean hasSpace() {
            return false;
        }

        @Override
        public Collection<Room> roomsWithCompletedProduct() {
            return List.of();
        }

        @Override
        public RoomsNeedingVillagerInput<Room, ?, ?> roomsNeedingIngredientsByState() {
            return null;
        }

        @Override
        public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
            return Map.of();
        }

        @Override
        public LZCD.Dependency<Void> hasSuppliesV2() {
            return new ConstantDep("has supplies [yes - test suite]", true);
        }

        @Override
        public boolean isUnfinishedTimeWorkPresent() {
            return false;
        }

        @Override
        public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
            return List.of();
        }

        @Override
        public Collection<Room> roomsAtState(Integer state) {
            return List.of();
        }
    };
    private EntityLocStateProvider<Room> entityLocation = new EntityLocStateProvider<Room>() {
        @Override
        public @Nullable Room getEntityCurrentJobSite() {
            return null;
        }
    };

    class TestWorkSpotStandIn implements DeclarativeJobWarping.WorkSpotStandIn<TestWorkSpotStandIn> {

        long timer = 0;
        State state = State.fresh();

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

    @Test
    public void testShouldReduceTimersByTicksPassed() {
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.timer = 200;
        TestWorkSpotStandIn next = DeclarativeJobWarping.warp(
                workspot,
                workspot,
                Warper.Tick.at(100).after(100),
                inventory,
                town,
                entityLocation,
                false,
                handler
        );
        Assertions.assertEquals(100, next.timer);
    }

    @Test
    public void testShouldReduceTimersToZeroAtMost() {
        // This is actually testing the test. But whatever...
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.timer = 200;
        TestWorkSpotStandIn next = DeclarativeJobWarping.warp(
                workspot,
                workspot,
                Warper.Tick.at(201).after(201),
                inventory,
                town,
                entityLocation,
                false,
                handler
        );
        Assertions.assertEquals(0, next.timer);
    }

    @Test
    public void testShouldHandleNullState() {
        // This is actually testing the test. But whatever...
        TestWorkSpotStandIn workspot = new TestWorkSpotStandIn();
        workspot.state = null;
        TestWorkSpotStandIn next = DeclarativeJobWarping.warp(
                workspot,
                workspot,
                Warper.Tick.at(100).after(100),
                inventory,
                town,
                entityLocation,
                false,
                handler
        );
        Assertions.assertEquals(State.fresh(), next.state);
    }
}