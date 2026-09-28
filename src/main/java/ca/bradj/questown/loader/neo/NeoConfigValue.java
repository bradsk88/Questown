package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/** NeoForge impl of the neutral {@link ConfigValue}; wraps {@code ModConfigSpec.ConfigValue}. */
public final class NeoConfigValue<T> implements ConfigValue<T> {
    private final ModConfigSpec.ConfigValue<T> value;

    public NeoConfigValue(ModConfigSpec.ConfigValue<T> value) {
        this.value = value;
    }

    @Nullable
    @Override
    public T get() {
        return value.get();
    }

    @Nullable
    @Override
    public T getDefault() {
        return value.getDefault();
    }
}
