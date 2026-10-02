package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A downtime townie's job has NO server-side status: getStatusForServer() is
// null. Posting a board request re-evaluates the worker pool, so the
// "is this worker busy?" check must treat a null status as not busy instead
// of NPE-ing (see .scratch/work-board-request/downtime-townie-listener-npe.md).
class IStatusTest {

    private static IStatus<NoopStatus> status(boolean busy) {
        return new NoopStatus(busy);
    }

    static final class NoopStatus implements IStatus<NoopStatus> {
        final boolean busy;

        NoopStatus(boolean busy) {
            this.busy = busy;
        }

        @Override
        public IStatusFactory<NoopStatus> getFactory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isGoingToJobsite() {
            return false;
        }

        @Override
        public boolean isDroppingLoot() {
            return false;
        }

        @Override
        public boolean isCollectingSupplies() {
            return false;
        }

        @Override
        public String name() {
            return "noop";
        }

        @Override
        public String nameV2() {
            return "noop";
        }

        @Override
        public boolean isUnset() {
            return false;
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            return !busy;
        }

        @Override
        public String getCategoryId() {
            return null;
        }

        @Override
        public boolean isBusy() {
            return busy;
        }
    }

    @Test
    void townieWithNoServerStatusIsNotBusy() {
        assertFalse(IStatus.isBusy((IStatus<?>) null));
    }

    @Test
    void busyStatusIsBusy() {
        assertTrue(IStatus.isBusy(status(true)));
    }

    @Test
    void idleStatusIsNotBusy() {
        assertFalse(IStatus.isBusy(status(false)));
    }
}
