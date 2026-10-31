package ca.bradj.questown.loader;

/**
 * A neutral, loader-agnostic builder for a runtime config file (Forge {@code ForgeConfigSpec.Builder}).
 * The {@code Config}/{@code RecipeItemConfig} spec classes build their values through this and the
 * caller-facing code (18 {@code Compat.configGet(...).get()} sites) is unchanged once the values are
 * neutral {@link ConfigValue} instances.
 *
 * <p>{@code push}/{@code pop}/{@code comment} return the builder for chaining and may also be used as plain
 * statements, matching the existing {@code Config} class usage.
 */
public interface ConfigBuilder {
    ConfigBuilder push(String path);

    ConfigBuilder pop();

    ConfigBuilder comment(String comment);

    <T> ConfigValue<T> define(String name, T defaultVal);

    <T extends Comparable<T>> ConfigValue<T> defineInRange(String name, T defaultVal, T min, T max);

    ConfigSpec build();
}
