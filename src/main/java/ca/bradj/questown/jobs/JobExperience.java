package ca.bradj.questown.jobs;

import java.util.Collection;

/** The XP for one job completion, the same in real time and during a warp. A job's declared {@code experience} replaces it. */
public final class JobExperience {

    private JobExperience() {
    }

    /** Work units times the action length, plus a tenth of the timed duration (at least 1). Zero for jobs that give no experience. */
    public static int formula(
            boolean noExperienceGained,
            Collection<Integer> workPerState,
            int actionDuration,
            Collection<Integer> timePerState
    ) {
        if (noExperienceGained) {
            return 0;
        }
        int workPart = workPerState.stream().reduce(0, Integer::sum) * actionDuration;
        long totalTime = timePerState.stream().mapToLong(Integer::longValue).sum();
        return (int) (workPart + Math.max(totalTime / 10, 1));
    }
}
