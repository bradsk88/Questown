package ca.bradj.questown.loader;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * A neutral, loader-agnostic read access to a runtime config value.
 * Drop-in for the old Forge {@code ForgeConfigSpec.ConfigValue}: the 18 call sites that do
 * {@code Compat.configGet(Config.X).get()} keep working once {@code Config.X} is this type.
 */
public interface ConfigValue<T> extends Supplier<T> {
    @Nullable
    @Override
    T get();

    @Nullable
    T getDefault();
}
