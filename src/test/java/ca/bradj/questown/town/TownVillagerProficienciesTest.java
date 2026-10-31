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
    void initializeIsIdempotentOnSecondCall() {
        // A second deserialization of the same flag's data (e.g. a chunk re-load firing
        // loadNextTick again) must not throw. The values are the same, so the existing
        // live values are kept and the redundant re-init is a no-op. This used to throw and
        // crash the server on a flag tick (run/crash-reports, "A Whole New Wordl").
        TownVillagerProficiencies p = new TownVillagerProficiencies();
        UUID u = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        p.initialize(Map.of(u, Map.of("farming", 0.5f)));
        Assertions.assertEquals(0.5f, p.getLevel(u, "farming"));
        // Second call does not throw...
        p.initialize(Map.of(u2, Map.of("smithing", 0.1f)));
        // ...and is a no-op: existing values are kept, the new (redundant) data is not merged.
        Assertions.assertEquals(0.5f, p.getLevel(u, "farming"));
        Assertions.assertFalse(p.isSeeded(u2));
        Assertions.assertEquals(0f, p.getLevel(u2, "smithing"));
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
