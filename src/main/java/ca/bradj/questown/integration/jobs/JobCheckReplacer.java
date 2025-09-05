package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.JobBlockTestContext;
import net.minecraft.core.BlockPos;

import java.util.function.Function;
import java.util.function.Predicate;

public class JobCheckReplacer {

    private JobCheck inner;

    public JobCheckReplacer(Predicate<JobBlockTestContext> jobBlock) {
        this.inner = jobBlock::test;
    }

    public void accept(Function<JobCheck, JobCheck> replacer) {
        this.inner = replacer.apply(inner);
    }

    public static Predicate<BlockPos> withContext(
            JobCheckReplacer jcr,
            JobBlockTestContext ctx
    ) {
        return p -> jcr.inner.test(ctx.withPos(p));
    }
}
