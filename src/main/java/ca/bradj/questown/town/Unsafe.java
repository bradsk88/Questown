package ca.bradj.questown.town;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Unsafe<T> {
    private final Class<?> owner;

    @Nullable
    T town = null;

    public Unsafe(Class<?> owner) {
        this.owner = owner;
    }

    public void initialize(T t) {
        this.town = t;
    }

    // Only safe to call after initialized
    public @NotNull T getUnsafe() {
        if (town == null) {
            throw new IllegalStateException(String.format("Reference has not been initialized on %s yet", owner));
        }
        return town;
    }
}
