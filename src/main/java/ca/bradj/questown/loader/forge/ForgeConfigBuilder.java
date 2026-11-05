package ca.bradj.questown.loader.forge;

import ca.bradj.questown.loader.ConfigBuilder;
import ca.bradj.questown.loader.ConfigSpec;
import ca.bradj.questown.loader.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec;

/** Forge 1.19.2 {@link ConfigBuilder}: a {@link ForgeConfigSpec.Builder}. */
public final class ForgeConfigBuilder implements ConfigBuilder {
    private final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

    @Override
    public ConfigBuilder push(String path) {
        builder.push(path);
        return this;
    }

    @Override
    public ConfigBuilder pop() {
        builder.pop();
        return this;
    }

    @Override
    public ConfigBuilder comment(String comment) {
        builder.comment(comment);
        return this;
    }

    @Override
    public <T> ConfigValue<T> define(String name, T defaultVal) {
        return new ForgeConfigValue<>(builder.define(name, defaultVal));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> ConfigValue<T> defineInRange(String name, T defaultVal, T min, T max) {
        if (defaultVal instanceof Integer) {
            return (ConfigValue<T>) (ConfigValue<?>) new ForgeConfigValue<>(builder.defineInRange(name, (Integer) defaultVal, (Integer) min, (Integer) max));
        }
        if (defaultVal instanceof Long) {
            return (ConfigValue<T>) (ConfigValue<?>) new ForgeConfigValue<>(builder.defineInRange(name, (Long) defaultVal, (Long) min, (Long) max));
        }
        if (defaultVal instanceof Double) {
            return (ConfigValue<T>) (ConfigValue<?>) new ForgeConfigValue<>(builder.defineInRange(name, (Double) defaultVal, (Double) min, (Double) max));
        }
        throw new UnsupportedOperationException("Forge defineInRange: unsupported value type " + defaultVal.getClass());
    }

    @Override
    public ConfigSpec build() {
        return new ForgeConfigSpecHandle(builder.build());
    }
}
