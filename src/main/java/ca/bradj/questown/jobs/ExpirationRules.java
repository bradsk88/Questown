package ca.bradj.questown.jobs;

import javax.annotation.Nullable;
import java.util.function.Function;
import java.util.function.Supplier;

public class ExpirationRules {

    private final Supplier<Long> maxTicksWithoutSupplies;
    private final Function<JobID, JobID> noSuppliesFallbackFn;
    private final Supplier<Long> maxTicks;
    private final Function<JobID, JobID> maxTicksFallbackFn;
    private @Nullable Long realizedTicks;
    private @Nullable Long realizedSupplyTicks;

    public ExpirationRules(
            Supplier<Long> maxTicksWithoutSupplies,
            Function<JobID, JobID> noSuppliesFallbackFn,
            Supplier<Long> maxTicks,
            Function<JobID, JobID> maxTicksFallbackFn
    ) {
        this.maxTicksWithoutSupplies = maxTicksWithoutSupplies;
        this.noSuppliesFallbackFn = noSuppliesFallbackFn;
        this.maxTicks = maxTicks;
        this.maxTicksFallbackFn = maxTicksFallbackFn;
    }

    public static ExpirationRules never() {
        return new ExpirationRules(
                () -> Long.MAX_VALUE,
                jobID -> jobID,
                () -> Long.MAX_VALUE,
                jobID -> jobID
        );
    }

    public long maxTicks() {
        if (this.realizedTicks == null) {
            this.realizedTicks = this.maxTicks.get();
        }
        return this.realizedTicks;
    }

    public Function<JobID, JobID> maxTicksFallbackFn() {
        return this.maxTicksFallbackFn;
    }

    public long maxTicksWithoutSupplies() {
        if (this.realizedSupplyTicks == null) {
            this.realizedSupplyTicks = this.maxTicksWithoutSupplies.get();
        }
        return this.realizedSupplyTicks;
    }

    public Function<JobID, JobID> noSuppliesFallbackFn() {
        return this.noSuppliesFallbackFn;
    }
}
