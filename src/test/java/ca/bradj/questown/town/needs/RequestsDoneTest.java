package ca.bradj.questown.town.needs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class RequestsDoneTest {

    private static final RequestsDone.JobView OPEN = new RequestsDone.JobView(true, false, false);

    private static final RequestsDone.JobView STOCKED = new RequestsDone.JobView(false, true, false);

    private static final RequestsDone.JobView UNREQUESTED = new RequestsDone.JobView(false, false, false);

    private static final RequestsDone.JobView SUPPORT_ON_OPEN = new RequestsDone.JobView(true, false, true);

    @Test
    void stockedJob_isDone() {
        Assertions.assertTrue(RequestsDone.allDone(List.of(STOCKED)));
    }

    @Test
    void openRequest_isNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(STOCKED, OPEN)));
    }

    @Test
    void supportJobOnAnOpenRequest_doesNotStopDone() {
        Assertions.assertTrue(RequestsDone.allDone(List.of(STOCKED, SUPPORT_ON_OPEN)));
    }

    @Test
    void onlySupportJobs_areNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(SUPPORT_ON_OPEN)));
    }

    @Test
    void nothingRequestedOfIt_isNotDone() {
        Assertions.assertFalse(RequestsDone.allDone(List.of(UNREQUESTED)));
        Assertions.assertFalse(RequestsDone.allDone(List.of()));
    }
}
