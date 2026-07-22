package ca.bradj.questown.town;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class TownVillagerProficienciesTest {

    @Test
    void unseededTownieReportsZeroAndEmpty() {
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        Assertions.assertFalse(p.isSeeded(u));
        Assertions.assertEquals(0f, p.getLevel(u, "farming"));
        Assertions.assertTrue(p.getAll(u).isEmpty());
    }

    @Test
    void setLevelRoundTrips() {
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        p.setLevel(u, "farming", 0.7f);
        Assertions.assertEquals(0.7f, p.getLevel(u, "farming"));
        Assertions.assertTrue(p.isSeeded(u));
        Assertions.assertEquals(0f, p.getLevel(u, "smithing"));
    }

    @Test
    void setAllReplacesWholeMapAndRoundTrips() {
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        Map<String, Float> seed = Map.of("farming", 0.2f, "smithing", 0.9f);
        p.setAll(u, seed);
        Assertions.assertEquals(seed, p.getAll(u));
        // Identity through the getAll() snapshot -> setAll() bridge (the warp round-trip shape).
        ImmutableMap<String, Float> snapshot = p.getAll(u);
        TownVillagerProficiencies q = new TownVillagerProficiencies();
        q.setAll(u, snapshot);
        Assertions.assertEquals(snapshot, q.getAll(u));
    }

    @Test
    void initializeRejectsSecondCall() {
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        p.initialize(Map.of(u, Map.of("farming", 0.5f)));
        Assertions.assertEquals(0.5f, p.getLevel(u, "farming"));
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> p.initialize(Map.of(UUID.randomUUID(), Map.of("smithing", 0.1f)))
        );
    }

    @Test
    void getAllUuidsSnapshotIsImmutableCopy() {
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        p.setLevel(u, "farming", 0.3f);
        ImmutableMap<UUID, ImmutableMap<String, Float>> all = p.getAll();
        Assertions.assertEquals(0.3f, all.get(u).get("farming"));
        // Mutating the holder after the snapshot does not change the snapshot.
        p.setLevel(u, "farming", 0.8f);
        Assertions.assertEquals(0.3f, all.get(u).get("farming"));
    }
}
