package ca.bradj.questown.town.needs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RequestsDoneTest {

    private static RequestsDone.JobView open(boolean workable) {
        return new RequestsDone.JobView(true, false, workable, false);
    }

    private static RequestsDone.JobView stocked(boolean workable) {
        return new RequestsDone.JobView(false, true, workable, false);
    }

    private static final RequestsDone.JobView UNREQUESTED = new RequestsDone.JobView(false, false, true, false);

    /** A fuel/extract job: it answers the open request its makers answer. */
    private static final RequestsDone.JobView SUPPORT_ON_OPEN = new RequestsDone.JobView(true, false, true, true);

    @Test
    void stockedWorkableJob_isDone() {
        Assertions.assertTrue(RequestsDone.allDone(List.of(stocked(true))));
    }

    @Test
    void workableOpenRequest_isNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(stocked(true), open(true))));
    }

    @Test
    void blockedOpenRequest_doesNotStopDone() {
        // The dev's idle cook (2026-10-30): mutton for the stocked food, no pork for the porkchop.
        Assertions.assertTrue(RequestsDone.allDone(List.of(stocked(true), open(false))));
    }

    @Test
    void supportJobOnAnOpenRequest_doesNotStopDone() {
        // cook/fuel [requests_done]: fuel and extract "answered" the porkchop request.
        Assertions.assertTrue(RequestsDone.allDone(List.of(stocked(true), open(false), SUPPORT_ON_OPEN)));
    }

    @Test
    void onlySupportJobs_areNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(SUPPORT_ON_OPEN)));
    }

    @Test
    void onlyBlockedJobs_areStruggling_notDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(stocked(false), open(false))));
    }

    @Test
    void nothingRequestedOfIt_isNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(UNREQUESTED)));
        Assertions.assertFalse(RequestsDone.allDone(List.of()));
    }

    @Test
    void okAndNoRequest_areWorkable_noRoomAndBlocked_arent() {
        Assertions.assertTrue(RequestsDone.workable(new GateReason.Ok()));
        Assertions.assertTrue(RequestsDone.workable(new GateReason.NoRequest()));
        Assertions.assertFalse(RequestsDone.workable(new GateReason.NoRoom(null, null)));
        Assertions.assertFalse(RequestsDone.workable(new GateReason.Blocked(0, List.of("x"), List.of())));
    }
}
