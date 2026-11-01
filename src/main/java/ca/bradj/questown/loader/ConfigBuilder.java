package ca.bradj.questown.loader;

/** A loader-neutral builder for a runtime config file (Forge's {@code ForgeConfigSpec.Builder}). */
public interface ConfigBuilder {
    ConfigBuilder push(String path);

    ConfigBuilder pop();

    ConfigBuilder comment(String comment);

    <T> ConfigValue<T> define(String name, T defaultVal);

    <T extends Comparable<T>> ConfigValue<T> defineInRange(String name, T defaultVal, T min, T max);

    ConfigSpec build();
}
