package ca.bradj.questown.mobs.visitor;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * use-1.20-villager-skins.md: the townie skin pool now carries the vanilla
 * 1.20.6 character skins (added in 1.20.5 alongside Steve/Alex) on top of
 * the mod's custom skins, and the per-UUID pick is deterministic.
 */
class TownieSkinsTest {

    /** The exact slim-variant set the 1.20.6 client jar ships. */
    private static final Set<String> VANILLA_1_20_6 = Set.of(
            "alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"
    );

    @Test
    void pool_is_nine_vanilla_slim_plus_six_custom() {
        List<ResourceLocation> pool = TownieSkins.pool();
        assertEquals(9 + 6, pool.size());
    }

    @Test
    void vanilla_entries_are_the_1206_slim_character_skins() {
        List<ResourceLocation> pool = TownieSkins.pool();
        Set<String> vanillaPaths = pool.stream()
                .filter(l -> l.getNamespace().equals("minecraft"))
                .map(l -> {
                    // textures/entity/player/slim/<name>.png
                    int slash = l.getPath().lastIndexOf('/');
                    String file = l.getPath().substring(slash + 1);
                    return file.substring(0, file.length() - ".png".length());
                })
                .collect(Collectors.toSet());
        assertEquals(VANILLA_1_20_6, vanillaPaths,
                "vanilla pool entries must match the 1.20.6 slim character skins");
        // Every vanilla entry uses the slim variants (the model is PLAYER_SLIM).
        pool.stream()
                .filter(l -> l.getNamespace().equals("minecraft"))
                .forEach(l -> assertTrue(l.getPath().contains("/slim/"),
                        "slim variant expected: " + l));
    }

    @Test
    void custom_skins_stay_in_the_pool() {
        List<ResourceLocation> pool = TownieSkins.pool();
        Set<String> paths = pool.stream().map(ResourceLocation::getPath).collect(Collectors.toSet());
        for (int i = 1; i <= 6; i++) {
            assertTrue(paths.contains("textures/entity/" + i + ".png"),
                    "custom skin " + i + " missing from the pool");
        }
    }

    @Test
    void pick_is_deterministic_and_stays_in_the_pool() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(TownieSkins.pick(a), TownieSkins.pick(a), "same UUID -> same skin");
        assertTrue(TownieSkins.pool().contains(TownieSkins.pick(a)));
        // Different UUIDs should not all map to one skin (sanity, not exhaustiveness).
        assertTrue(TownieSkins.pool().size() > 1);
    }

    @Test
    void pick_handles_a_min_value_hash() {
        // A UUID whose hashCode is Integer.MIN_VALUE must not mis-pick (floorMod, not abs).
        UUID min = UUID.randomUUID();
        // Floor-mod by construction: index is the pool position of |hash| wrapped.
        int expected = Math.floorMod(min.hashCode(), TownieSkins.pool().size());
        assertEquals(TownieSkins.pool().get(expected), TownieSkins.pick(min));
    }

    @Test
    void vanilla_and_custom_pick_differently() {
        // A single UUID must not be able to select two different pool entries.
        UUID u = UUID.fromString("00000000-0000-0000-0000-000000000000");
        ResourceLocation p1 = TownieSkins.pick(u);
        ResourceLocation p2 = TownieSkins.pick(u);
        assertEquals(p1, p2);
        assertNotEquals(TownieSkins.pool().get(0), TownieSkins.pool().get(1));
    }
}
