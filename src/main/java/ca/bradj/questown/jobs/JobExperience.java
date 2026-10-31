package ca.bradj.questown.jobs;

import java.util.Collection;

/**
 * The XP formula for one job completion, shared by the realtime grant
 * ({@code DeclarativeJob.getExperienceEarned}) and the warp grant
 * ({@code TimeWarpWorldInteraction.onJobCompleted}), so both paths pay the same for the same job.
 * A job's declared `experience` replaces it ({@link Work#experienceEarned}). Pinned by
 * {@code JobExperienceTest}.
 */
public final class JobExperience {

    private JobExperience() {
    }

    /**
     * Work units across the states x the action length, plus a tenth of the timed duration (at
     * least 1). Zero for a job with {@code no_experience_gained} (the BOP deposit, the diners,
     * downtime).
     */
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
