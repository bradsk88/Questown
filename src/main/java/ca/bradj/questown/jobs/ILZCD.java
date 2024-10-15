package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface ILZCD<T> {
    void initializeAll();

    @Nullable T resolve();

    LZCD.Populated<T> populate();

    boolean isValueNull(T value);
}
