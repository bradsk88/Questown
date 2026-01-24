package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.integration.TestRoomMatch;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for TickTownProvider.
 */
class TickTownProviderTest {

    // Must match IntegrationTestWorld.DEFAULT_WORKSPOT_POS which TestRoomMatch.defaultRoom() uses
    private static final Position WORKSPOT_POS = new Position(0, 0);

    /**
     * Creates a simple test town with one villager, one room, and infinite storage.
     * Only the parameters relevant to the test need to be specified.
     */
    private static class SimpleTownBuilder {
        private final Map<Position, State> blockStates = new HashMap<>();
        private final Map<Position, Integer> timers = new HashMap<>();
        private ImmutableList<TestRoomMatch> roomsWithCompletedProduct = ImmutableList.of();
        private ImmutableList<TestRoomMatch> allJobSites = ImmutableList.of();
        private int maxState = 3;

        SimpleTownBuilder withBlockState(Position pos, State state) {
            blockStates.put(pos, state);
            return this;
        }

        SimpleTownBuilder withTimer(Position pos, int ticksRemaining) {
            timers.put(pos, ticksRemaining);
            return this;
        }

        SimpleTownBuilder withRoomsWithCompletedProduct(ImmutableList<TestRoomMatch> rooms) {
            this.roomsWithCompletedProduct = rooms;
            return this;
        }

        SimpleTownBuilder withAllJobSites(ImmutableList<TestRoomMatch> rooms) {
            this.allJobSites = rooms;
            return this;
        }

        SimpleTownBuilder withMaxState(int maxState) {
            this.maxState = maxState;
            return this;
        }

        TickTownProvider<Room, Position, TestRoomMatch, GathererJournalTest.TestItem, TestTownItem, ContainerTarget<?, TestTownItem>> build() {
            return new TickTownProvider<>(
                    () -> roomsWithCompletedProduct,
                    () -> allJobSites,
                    () -> allJobSites,
                    m -> true,
                    (r, p) -> { throw new UnsupportedOperationException(); },
                    blockStates::get,
                    timers::get,
                    new RoomsNeedingVillagerInput<>(ImmutableMap.of()),
                    p -> p.equals(WORKSPOT_POS),
                    p -> true,
                    s -> { throw new UnsupportedOperationException(); },
                    s -> { throw new UnsupportedOperationException(); },
                    Position::toString,
                    maxState,
                    s -> { throw new UnsupportedOperationException(); },
                    () -> true,
                    () -> new Signals.DayTime(0)
            );
        }
    }

    /**
     * Bug reproduction test: isUnfinishedTimeWorkPresent() returns false when timer is active
     * but state < maxState.
     *
     * Root cause: isUnfinishedTimeWorkPresent() uses resultsFinder (getRoomsWithCompletedProduct)
     * which only returns rooms at maxState. When a timer is active, the room is NOT at maxState,
     * so resultsFinder returns empty, and isUnfinishedTimeWorkPresent() returns false.
     *
     * This test FAILS while the bug exists, PASSES when fixed.
     *
     * Fix: Change line 99 from `resultsFinder` to `roomsFinder`.
     */
    @Test
    void isUnfinishedTimeWorkPresent_shouldReturnTrue_whenTimerActiveButStateNotAtMax() {
        TestRoomMatch room = TestRoomMatch.defaultRoom("baker");

        // resultsFinder returns EMPTY because state 2 < maxState 3 (no completed product)
        TickTownProvider<?, ?, ?, ?, ?, ?> provider = new SimpleTownBuilder()
                .withBlockState(WORKSPOT_POS, State.freshAtState(2))
                .withTimer(WORKSPOT_POS, 500)
                .withRoomsWithCompletedProduct(ImmutableList.of())  // Empty - this is the bug trigger
                .withAllJobSites(ImmutableList.of(room))
                .withMaxState(3)
                .build();

        boolean result = provider.isUnfinishedTimeWorkPresent();

        Assertions.assertTrue(result, "BUG: isUnfinishedTimeWorkPresent() returns FALSE when timer is active");
    }

    /**
     * Sanity check: isUnfinishedTimeWorkPresent() returns true when room IS at maxState
     * with active timer (this case works correctly).
     */
    @Test
    void isUnfinishedTimeWorkPresent_shouldReturnTrue_whenTimerActiveAndStateAtMax() {
        TestRoomMatch room = TestRoomMatch.defaultRoom("baker");

        // resultsFinder returns the room because state == maxState
        TickTownProvider<?, ?, ?, ?, ?, ?> provider = new SimpleTownBuilder()
                .withBlockState(WORKSPOT_POS, State.freshAtState(3))
                .withTimer(WORKSPOT_POS, 500)
                .withRoomsWithCompletedProduct(ImmutableList.of(room))
                .withAllJobSites(ImmutableList.of(room))
                .withMaxState(3)
                .build();

        boolean result = provider.isUnfinishedTimeWorkPresent();

        Assertions.assertTrue(result, "Should return true when room is at maxState with active timer");
    }

    /**
     * Sanity check: isUnfinishedTimeWorkPresent() returns false when no timer is active.
     */
    @Test
    void isUnfinishedTimeWorkPresent_shouldReturnFalse_whenNoTimerActive() {
        TestRoomMatch room = TestRoomMatch.defaultRoom("baker");

        // No timer set - this is what we're testing
        TickTownProvider<?, ?, ?, ?, ?, ?> provider = new SimpleTownBuilder()
                .withBlockState(WORKSPOT_POS, State.freshAtState(3))
                // No withTimer() call - no timer
                .withRoomsWithCompletedProduct(ImmutableList.of(room))
                .withAllJobSites(ImmutableList.of(room))
                .withMaxState(3)
                .build();

        boolean result = provider.isUnfinishedTimeWorkPresent();

        Assertions.assertFalse(result, "Should return false when no timer is active");
    }

    // Minimal test item type for the generic parameter
    private static class TestTownItem implements Item<TestTownItem> {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean isFood() {
            return false;
        }

        @Override
        public TestTownItem shrink() {
            return this;
        }

        @Override
        public String getShortName() {
            return "test";
        }

        @Override
        public TestTownItem unit() {
            return this;
        }

        @Override
        public int quantity() {
            return 0;
        }
    }
}