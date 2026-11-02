package ca.bradj.questown.town.needs;

import java.util.Collection;

/** Whether an idle villager's requests are all stocked, so it says so and shows no bubble. An open request it can't fill keeps it from being done. Support jobs don't count. */
public final class RequestsDone {

    private RequestsDone() {
    }

    /**
     * One of the villager's unlocked jobs.
     *
     * @param answersOpenRequest    it makes something an unpaused request wants
     * @param answersStockedRequest it makes something only a paused request wants
     * @param makesNothing          a support job (fuel, extract, stock, display)
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
