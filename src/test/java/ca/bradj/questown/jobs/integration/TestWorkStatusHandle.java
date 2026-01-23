package ca.bradj.questown.jobs.integration;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.blacksmith.MapBackedWSC;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.core.space.Position;
import org.jetbrains.annotations.Nullable;

/**
 * Test implementation of WorkStatusHandle for integration tests.
 */
public class TestWorkStatusHandle extends MapBackedWSC implements WorkStatusHandle<Position, GathererJournalTest.TestItem> {

    @Override
    public boolean canInsertItem(GathererJournalTest.TestItem item, Position bp) {
        return true;
    }

    @Override
    public @Nullable Integer getTimeToNextState(Position bp) {
        return getTimer(bp);
    }

    @Override
    public void drainAllTimers() {
        // No-op for tests - timers handled differently
    }
}
