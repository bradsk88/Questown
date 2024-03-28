package ca.bradj.questown.jobs;

import java.util.function.BiConsumer;

public interface BiIterable<T, T1> {
    void forEach(BiConsumer<T, T1> c);
}
