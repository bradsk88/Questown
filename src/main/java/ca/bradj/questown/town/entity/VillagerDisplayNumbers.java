package ca.bradj.questown.town.entity;

import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "Villager N" numbers: a counter and the assignments. A number is never reused. */
public final class VillagerDisplayNumbers {
    private final Map<UUID, Integer> byVuid = new HashMap<>();
    private int highest = 0;

    public int numberFor(UUID vuid) {
        return byVuid.getOrDefault(vuid, 0);
    }

    /** Gives the next number, unless {@code vuid} already has one. */
    public int assign(UUID vuid) {
        return byVuid.computeIfAbsent(vuid, v -> ++highest);
    }

    /** "Villager N", translatable so the client resolves it. */
    public static Component nameplate(int n) {
        return Compat.translatable("menu.villager.name", n);
    }

    public void retire(UUID vuid) {
        byVuid.remove(vuid);
    }

    Map<UUID, Integer> byVuid() {
        return byVuid;
    }

    int highest() {
        return highest;
    }

    /** Restores from save; the counter stays at least as high as any stored number. */
    public void restore(Map<UUID, Integer> stored, int storedHighest) {
        byVuid.clear();
        byVuid.putAll(stored);
        int maxStored = stored.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        highest = Math.max(storedHighest, maxStored);
    }
}
