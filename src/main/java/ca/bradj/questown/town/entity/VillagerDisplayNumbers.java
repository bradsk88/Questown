package ca.bradj.questown.town.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure core of the townie display numbers ("Villager N"): a monotonic counter plus the
 * assignment map. Lives in its own Forge-free class so the no-reuse ruling (2026-10-14
 * playtest: "when a villager dies/leaves its number is retired, never re-issued") is
 * unit-testable without loading {@link TownVillagerHandle} (whose constructor reads
 * config, which unit tests cannot do).
 *
 * The counter is monotonic: a number stays retired once issued, even when the townie
 * holding it is removed from the map.
 */
public final class VillagerDisplayNumbers {
    private final Map<UUID, Integer> byVuid = new HashMap<>();
    private int highest = 0;

    /** The display number for {@code vuid}, or 0 if none assigned yet. */
    public int numberFor(UUID vuid) {
        return byVuid.getOrDefault(vuid, 0);
    }

    /**
     * Assign the next number ({@code highest + 1}) to {@code vuid}. Idempotent: a vuid
     * that already has a number keeps it (the reload-from-NBT path).
     */
    public int assign(UUID vuid) {
        return byVuid.computeIfAbsent(vuid, v -> ++highest);
    }

    /** Retire the townie's number (it leaves/dies). The number is never re-issued. */
    public void retire(UUID vuid) {
        byVuid.remove(vuid);
    }

    Map<UUID, Integer> byVuid() {
        return byVuid;
    }

    int highest() {
        return highest;
    }

    /** Restore from NBT: {@code highest} must stay at least as large as any stored value. */
    public void restore(Map<UUID, Integer> stored, int storedHighest) {
        byVuid.clear();
        byVuid.putAll(stored);
        int maxStored = stored.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        highest = Math.max(storedHighest, maxStored);
    }
}
