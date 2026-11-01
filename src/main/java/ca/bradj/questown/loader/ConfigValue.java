package ca.bradj.questown.loader;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Loader-neutral read access to a runtime config value. */
public interface ConfigValue<T> extends Supplier<T> {
    @Nullable
    @Override
    T get();

    @Nullable
    T getDefault();
}
