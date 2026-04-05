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
}
