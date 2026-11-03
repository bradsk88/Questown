package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each villager's proficiency levels, 0 to 1, in real time. The warp keeps its own copy, synced each warp. */
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

    /** True once the villager has a proficiency record, so a loaded villager isn't given new random ones. */
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
            // A second load of the same data keeps the current values.
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
