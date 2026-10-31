package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The live (realtime) per-townie proficiency store, mirroring {@link TownVillagerMoods}:
 * a {@code Map<UUID, Map<proficiency-id, level∈[0,1]>>}. The warp-side carrier is
 * {@link TownState.VillagerData#getProficiencies()}; {@code TownFlagState} bridges the
 * two each warp round-trip. See ADR-0010.
 */
public class TownVillagerProficiencies {

    private final Map<UUID, Map<String, Float>> levels = new HashMap<>();

    public float getLevel(
            UUID uuid,
            String proficiencyId
    ) {
        Map<String, Float> m = levels.get(uuid);
        if (m == null) {
            return 0f;
        }
        return m.getOrDefault(proficiencyId, 0f);
    }

    public ImmutableMap<String, Float> getAll(UUID uuid) {
        Map<String, Float> m = levels.get(uuid);
        if (m == null) {
            return ImmutableMap.of();
        }
        return ImmutableMap.copyOf(m);
    }

    /**
     * True once the townie has a (possibly empty) map on record — used to decide whether
     * spawn-seeding should run (loaded/relocated townies keep theirs).
     */
    public boolean isSeeded(UUID uuid) {
        return levels.containsKey(uuid);
    }

    public void setLevel(
            UUID uuid,
            String proficiencyId,
            float level
    ) {
        levels.computeIfAbsent(uuid, k -> new HashMap<>()).put(proficiencyId, level);
    }

    public void setAll(
            UUID uuid,
            Map<String, Float> map
    ) {
        levels.put(uuid, new HashMap<>(map));
    }

    public void initialize(Map<UUID, ? extends Map<String, Float>> init) {
        if (!levels.isEmpty()) {
            // Idempotent. A second deserialization of the same flag's data (for example on a
            // chunk re-load) must not throw; the values are the same, so the existing live
            // values are kept. This used to throw and crash the server on a flag tick.
            QT.VILLAGER_LOGGER.warn(
                    "Villager proficiencies already initialized; ignoring re-initialization "
                            + "and keeping existing values"
            );
            return;
        }
        init.forEach((k, v) -> levels.put(k, new HashMap<>(v)));
    }

    public ImmutableMap<UUID, ImmutableMap<String, Float>> getAll() {
        ImmutableMap.Builder<UUID, ImmutableMap<String, Float>> b = ImmutableMap.builder();
        levels.forEach((k, v) -> b.put(k, ImmutableMap.copyOf(v)));
        return b.build();
    }
}
