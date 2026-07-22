package ca.bradj.questown.jobs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Pure proficiency math: the work-speed multiplier curve and the gain/decay
 * clamps. No Minecraft or Forge types so JUnit can drive it directly.
 * <p>
 * A proficiency <em>level</em> is always in {@code [0, 1]}. The multiplier is a
 * linear interpolation between {@code min} (level 0) and {@code max} (level 1).
 * See ADR-0010.
 */
public final class Proficiency {

    private Proficiency() {
    }

    /**
     * The effective work-speed multiplier for a townie at {@code level} in the
     * band {@code [min, max]}. Level is clamped to {@code [0, 1]} first.
     */
    public static float multiplierFor(
            float level,
            float min,
            float max
    ) {
        float clamped = clampLevel(level);
        return min + clamped * (max - min);
    }

    /**
     * Adds {@code gainPerTick * durationTicks} to {@code level}, clamped to
     * {@code [0, 1]}.
     */
    public static float applyGain(
            float level,
            float gainPerTick,
            int durationTicks
    ) {
        return clampLevel(level + gainPerTick * durationTicks);
    }

    /**
     * Subtracts {@code decayPerTick * durationTicks} from {@code level}, clamped
     * to {@code [0, 1]}.
     */
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

    /**
     * Apply one completed work action to a townie's proficiency map: {@code workedId} gains
     * {@code gainPerTick × durationTicks} (created at 0 if absent), and every <em>other</em> held
     * proficiency decays {@code decayPerTick × durationTicks} — all clamped to {@code [0,1]}.
     * Pure so both the realtime and warp paths call the identical math (the leveling-parity crux
     * of ADR-0010).
     */
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

    /**
     * Deterministically seed a fresh townie with up to {@code count} distinct proficiencies
     * drawn from {@code declaredIds}, each at a random level in {@code [0,1)}. The draw is
     * reproducible: the same UUID and declared-id set always produce the same map (the ids
     * are sorted into a stable order before a UUID-seeded RNG shuffles them). If fewer than
     * {@code count} ids are declared, seeds as many as exist. See ADR-0010.
     */
    public static Map<String, Float> seed(
            UUID uuid,
            Collection<String> declaredIds,
            int count
    ) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (count <= 0 || declaredIds.isEmpty()) {
            return out;
        }
        // Sort to a stable order so a HashSet's iteration order can't perturb the draw.
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
