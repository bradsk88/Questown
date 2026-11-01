package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/** Forge 1.19.2 {@link ConfigValue}. */
public final class ForgeConfigValue<T> implements ConfigValue<T> {
    private final ForgeConfigSpec.ConfigValue<T> value;

    public ForgeConfigValue(ForgeConfigSpec.ConfigValue<T> value) {
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
