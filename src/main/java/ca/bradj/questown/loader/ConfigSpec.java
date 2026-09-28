package ca.bradj.questown.loader;

/**
 * A neutral, built config spec (Forge {@code ForgeConfigSpec}). The active loader unwraps this into its own
 * spec type when registering it, so main code never names the loader spec class.
 */
public interface ConfigSpec {
    // Marker for a built spec; the loader impl casts to its concrete type on registration.
}
