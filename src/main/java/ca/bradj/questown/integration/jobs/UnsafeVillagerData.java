package ca.bradj.questown.integration.jobs;

/**
 * Do not expect this data to survive a server restart. It might also get overwritten by other mods.
 */
public interface UnsafeVillagerData {
    String get(String key);
    void write(String key, String value);

    void clear(String key);
}
