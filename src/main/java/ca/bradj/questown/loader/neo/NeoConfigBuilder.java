package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ConfigBuilder;
import ca.bradj.questown.loader.ConfigSpec;
import ca.bradj.questown.loader.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge impl of the neutral {@link ConfigBuilder}; delegates to {@code ModConfigSpec.Builder}.
 * {@code define} is a straight generic pass-through; {@code defineInRange} dispatches on the value type
 * because NeoForge exposes primitive-specialised overloads (Int/Long/Double) rather than one generic.
 */
public final class NeoConfigBuilder implements ConfigBuilder {
    private final ModConfigSpec.Builder builder;

    public NeoConfigBuilder() {
        this.builder = new ModConfigSpec.Builder();
    }

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
        return new NeoConfigValue<>(builder.define(name, defaultVal));
    }

    @Override
    public <T extends Comparable<T>> ConfigValue<T> defineInRange(String name, T defaultVal, T min, T max) {
        if (defaultVal instanceof Integer) {
            return new NeoConfigValue<>(builder.defineInRange(name, (int) defaultVal, (int) min, (int) max));
        }
        if (defaultVal instanceof Long) {
            return new NeoConfigValue<>(builder.defineInRange(name, (long) defaultVal, (long) min, (long) max));
        }
        if (defaultVal instanceof Double) {
            return new NeoConfigValue<>(builder.defineInRange(name, (double) defaultVal, (double) min, (double) max));
        }
        throw new UnsupportedOperationException("Neo defineInRange: unsupported value type " + defaultVal.getClass());
    }

    @Override
    public ConfigSpec build() {
        return new NeoConfigSpec(builder.build());
    }
}
