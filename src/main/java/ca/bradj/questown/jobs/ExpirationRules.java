package ca.bradj.questown.jobs;

import java.util.function.Function;

public record ExpirationRules(
        long maxTicksWithoutSupplies,
        Function<JobID, JobID> noSuppliesFallbackFn,
        long maxTicks,
        Function<JobID, JobID> maxTicksFallbackFn
) {
    public static ExpirationRules never() {
        return new ExpirationRules(
                Integer.MAX_VALUE,
                jobID -> jobID,
                Integer.MAX_VALUE,
                jobID -> jobID
        );
    }
}
