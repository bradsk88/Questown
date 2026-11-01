package ca.bradj.questown.jobs;

import java.util.Map;

/**
 * The villager level at which each armor material's pieces become unlockable: leather 3, iron 8,
 * gold 11, diamond 14, beside the tool tiers' 3/5/8/11/14. Keyed by the armor item prefix the
 * armorer template expands into.
 */
public final class ArmorTierLevels {

    private static final Map<String, Integer> LEVELS = Map.of(
            "leather", 3,
            "iron", 8,
            "golden", 11,
            "diamond", 14
    );

    private ArmorTierLevels() {
    }

    /** The level for an armor prefix ("leather", "iron", "golden", "diamond"); unknown = the default. */
    public static int forPrefix(String prefix) {
        return LEVELS.getOrDefault(prefix, Work.DEFAULT_AVAILABLE_AT_LEVEL);
    }
}
