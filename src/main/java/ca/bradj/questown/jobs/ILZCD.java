package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface ILZCD<T> {
    @Nullable T resolve();

    LZCD.Populated<T> populate();

    boolean isValueNull(T value);
}
