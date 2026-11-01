package ca.bradj.questown.town.needs;

import java.util.Collection;

/**
 * Whether an idle villager has done all its job-board requests: every request its jobs answer is
 * stocked. Then it says "All my requests are done" and shows no need bubble. An open request keeps
 * it from being done even when its job can't run: a cook with a porkchop request and no raw pork
 * shows the raw-pork bubble, and with pork but no coal it shows coal. Only jobs that make something
 * decide it: a support job (fuel, extract, stock) answers whatever its makers answer.
 */
public final class RequestsDone {

    private RequestsDone() {
    }

    /**
     * One of the villager's unlocked jobs, as the board sees it.
     *
     * @param answersOpenRequest    the job makes something an open (unpaused) request wants
     * @param answersStockedRequest the job makes something only a paused (stocked) request wants
     * @param makesNothing          a support job (fuel, extract, stock, display), skipped
     */
    public record JobView(
            boolean answersOpenRequest,
            boolean answersStockedRequest,
            boolean makesNothing
    ) {
    }

    public static boolean allDone(Collection<JobView> jobs) {
        boolean fillsStocked = false;
        for (JobView j : jobs) {
            if (j.makesNothing()) {
                continue;
            }
            if (j.answersOpenRequest()) {
                return false;
            }
            fillsStocked |= j.answersStockedRequest();
        }
        return fillsStocked;
    }
}
