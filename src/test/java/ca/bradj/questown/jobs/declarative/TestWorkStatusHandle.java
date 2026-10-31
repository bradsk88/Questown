package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;

/**
 * Test implementation of WorkStatusHandle that extends AbstractWorkStatusStore
 * to get real timer countdown behavior.
 */
public class TestWorkStatusHandle extends AbstractWorkStatusStore<Position, GathererJournalTest.TestItem, Room, Object> {

    // A simple test room for timer processing
    private static final Room TEST_ROOM = new Room(
            new Position(0, 0),
            InclusiveSpace.from(0, 0).to(10, 10)
    );

    public TestWorkStatusHandle() {
        super(
                // posFactory - just return the position wrapped in a collection
                (room, pos) -> ImmutableList.of(pos),
                // airCheck - blocks are never air in tests
                (tickSource, pos) -> false,
                // defaultStateFactory - mirror the production TownWorkStatusStore: a
                // welcome mat is seeded unconditionally (the "whoever-seeds-first" race
                // was removed there; State.welcomeMatSeedState, 2026-10-07 dev directive).
                // The test world's job block is IntegrationTestWorld.DEFAULT_WORKSPOT_POS
                // (the gate mat), so seed exactly that. A null factory here would assert a
                // world that production never builds.
                (tickSource, pos) -> pos.equals(IntegrationTestWorld.DEFAULT_WORKSPOT_POS)
                        ? ca.bradj.questown.town.workstatus.State.welcomeMatSeedState()
                        : null,
                // cascadingBlockRevealer - no cascading
                (tickSource, pos) -> null
        );
    }

    /**
     * Tick the work status store to decrement timers and advance states.
     * Call this to simulate time passing.
     */
    public void tick(long ticksSinceLast) {
        // Use a test room so that doTick() gets called
        tick(null, ImmutableList.of(TEST_ROOM), ticksSinceLast);
    }

    /**
     * Tick once (1 tick).
     */
    public void tick() {
        tick(1);
    }
}
