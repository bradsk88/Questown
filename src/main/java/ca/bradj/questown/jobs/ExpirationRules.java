package ca.bradj.questown.jobs;

import java.util.function.Function;

public record ExpirationRules(
        long maxTicksWithoutSupplies,
        Function<JobID, JobID> fallbackFunc
) {
    public static ExpirationRules never() {
        return new ExpirationRules(
                Integer.MAX_VALUE,
                jobID -> jobID
        );
    }
}
