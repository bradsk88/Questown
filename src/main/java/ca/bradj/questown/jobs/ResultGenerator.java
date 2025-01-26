package ca.bradj.questown.jobs;

import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

public interface ResultGenerator<T> {
    Iterable<T> generate(
            ServerLevel level,
            Collection<T> heldItems
    );

    boolean isResultAlwaysEmpty();
}
