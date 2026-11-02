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

    /** For result types whose own cycle yields nothing: {@code uses_special_rules} and {@code via_other_job}. */
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
                // It does produce, just through another job.
                return false;
            }
        };
    }
}
