package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

public interface ResultGenerator<T> {
    Iterable<T> generate(
            ServerLevel level,
            Collection<T> heldItems
    );

    boolean isResultAlwaysEmpty();

    static <T> ResultGenerator<T> alwaysEmpty() {
        return new ResultGenerator<>() {
            @Override
            public Iterable<T> generate(ServerLevel level, Collection<T> heldItems) {
                return ImmutableList.of();
            }

            @Override
            public boolean isResultAlwaysEmpty() {
                return true;
            }
        };
    }

    /**
     * The generator for a result type whose own cycle yields nothing: {@code uses_special_rules}
     * (a rule moves items, e.g. cook/fuel) and {@code via_other_job} (the job's declared product
     * comes out through another job, e.g. cook/mutton's cooked mutton via cook/extract).
     */
    static <T> ResultGenerator<T> forSpecialType(String resultType) {
        if (!"via_other_job".equals(resultType)) {
            return alwaysEmpty();
        }
        return new ResultGenerator<>() {
            @Override
            public Iterable<T> generate(ServerLevel level, Collection<T> heldItems) {
                return ImmutableList.of();
            }

            @Override
            public boolean isResultAlwaysEmpty() {
                // The job does produce (its declared product), just not from its own cycle.
                return false;
            }
        };
    }
}
