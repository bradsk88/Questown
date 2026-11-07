package ca.bradj.questown.jobs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

/** Proficiency math. A level is in [0, 1]; the work-speed multiplier runs from {@code min} at 0 to {@code max} at 1. */
public final class Proficiency {

    private Proficiency() {
    }

    public static float multiplierFor(
            float level,
            float min,
            float max
    ) {
        float clamped = clampLevel(level);
        return min + clamped * (max - min);
    }

    public static float applyGain(
            float level,
            float gainPerTick,
            int durationTicks
    ) {
        return clampLevel(level + gainPerTick * durationTicks);
    }

    public static float applyDecay(
            float level,
            float decayPerTick,
            int durationTicks
    ) {
        return clampLevel(level - decayPerTick * durationTicks);
    }

    private static float clampLevel(float level) {
        return Math.max(0f, Math.min(1f, level));
    }

    /** One completed work action: the worked proficiency grows and every other one decays, all within [0, 1]. */
    public static Map<String, Float> levelUp(
            Map<String, Float> current,
            String workedId,
            float gainPerTick,
            float decayPerTick,
            int durationTicks
    ) {
        Map<String, Float> out = new LinkedHashMap<>(current);
        out.put(workedId, applyGain(current.getOrDefault(workedId, 0f), gainPerTick, durationTicks));
        for (Map.Entry<String, Float> e : current.entrySet()) {
            if (e.getKey().equals(workedId)) {
                continue;
            }
            out.put(e.getKey(), applyDecay(e.getValue(), decayPerTick, durationTicks));
        }
        return out;
    }

    /** Up to {@code count} random proficiencies for a new villager. The same UUID always gives the same result. */
    public static Map<String, Float> seed(
            UUID uuid,
            Collection<String> declaredIds,
            int count
    ) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (count <= 0 || declaredIds.isEmpty()) {
            return out;
        }
        List<String> pool = new ArrayList<>(new TreeSet<>(declaredIds));
        Random rng = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        java.util.Collections.shuffle(pool, rng);
        int n = Math.min(count, pool.size());
        for (int i = 0; i < n; i++) {
            out.put(pool.get(i), rng.nextFloat());
        }
        return out;
    }
}
