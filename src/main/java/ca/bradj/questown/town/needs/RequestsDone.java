package ca.bradj.questown.town.needs;

import java.util.Collection;

/**
 * Whether an idle villager has done all its job-board requests (dev ruling 2026-10-30): every
 * request it could fill is stocked. Then it says "All my requests are done" and shows no need
 * bubble. A request it can't fill (its job lacks supplies or a room) doesn't keep it from being
 * done: the dev's idle cook had mutton for the stocked food requests and no pork for a porkchop.
 * Only jobs that make something decide it: a support job (fuel, extract, stock) answers whatever
 * its makers answer, so it would "answer" the porkchop request with no pork in town.
 */
public final class RequestsDone {

    private RequestsDone() {
    }

    /**
     * One of the villager's unlocked jobs, as the board sees it.
     *
     * @param answersOpenRequest    the job makes something an open (unpaused) request wants
     * @param answersStockedRequest the job makes something only a paused (stocked) request wants
     * @param workable              nothing blocks the job (no missing room, supply or tool)
     * @param makesNothing          a support job (fuel, extract, stock, display), skipped
     */
    public record JobView(
            boolean answersOpenRequest,
            boolean answersStockedRequest,
            boolean workable,
            boolean makesNothing
    ) {
    }

    public static boolean allDone(Collection<JobView> jobs) {
        boolean fillsStocked = false;
        for (JobView j : jobs) {
            if (!j.workable() || j.makesNothing()) {
                continue;
            }
            if (j.answersOpenRequest()) {
                return false;
            }
            fillsStocked |= j.answersStockedRequest();
        }
        return fillsStocked;
    }

    /** Ok and NoRequest leave the job runnable; NoRoom and Blocked don't. */
    public static boolean workable(GateReason reason) {
        return reason instanceof GateReason.Ok || reason instanceof GateReason.NoRequest;
    }
}
