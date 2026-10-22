package ca.bradj.questown.town.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsorbedJournalsTest {

    private static final String TOWN_STATE = "town_state";

    private static CompoundTag villagerTag(UUID uuid, String job) {
        CompoundTag v = new CompoundTag();
        v.putUUID("UUID", uuid);
        v.putString("job", job);
        return v;
    }

    private static void putTownState(CompoundTag flagTag, CompoundTag... villagers) {
        ListTag list = new ListTag();
        for (CompoundTag v : villagers) {
            list.add(v);
        }
        CompoundTag state = new CompoundTag();
        state.put("villagers", list);
        flagTag.put(TOWN_STATE, state);
    }

    @Test
    void respawnTakesTheAbsorbTimeSnapshot() {
        CompoundTag flag = new CompoundTag();
        UUID baker = UUID.randomUUID();
        AbsorbedJournals.remember(flag, baker, villagerTag(baker, "baker/bread"));

        Optional<CompoundTag> got = AbsorbedJournals.take(flag, baker);

        assertEquals("baker/bread", got.orElseThrow().getString("job"));
    }

    @Test
    void snapshotIsConsumedSoASecondRespawnDoesNotReuseIt() {
        CompoundTag flag = new CompoundTag();
        UUID baker = UUID.randomUUID();
        AbsorbedJournals.remember(flag, baker, villagerTag(baker, "baker/bread"));

        AbsorbedJournals.take(flag, baker);

        assertTrue(AbsorbedJournals.take(flag, baker).isEmpty());
    }

    @Test
    void adoptingTheTownStateKeepsAnAbsorbTimeSnapshot() {
        CompoundTag flag = new CompoundTag();
        UUID v = UUID.randomUUID();
        putTownState(flag, villagerTag(v, "gatherer"));
        AbsorbedJournals.remember(flag, v, villagerTag(v, "baker/bread"));

        AbsorbedJournals.adoptTownState(flag, TOWN_STATE);

        assertEquals("baker/bread", AbsorbedJournals.take(flag, v).orElseThrow().getString("job"));
    }

    @Test
    void adoptingTheTownStateSnapshotsTowniesWhoWereNeverAbsorbed() {
        // A flag that went dormant before snapshots existed, or without the recall ritual.
        CompoundTag flag = new CompoundTag();
        UUID cook = UUID.randomUUID();
        UUID farmer = UUID.randomUUID();
        putTownState(flag, villagerTag(cook, "cook"), villagerTag(farmer, "farmer/wheat"));

        AbsorbedJournals.adoptTownState(flag, TOWN_STATE);

        assertEquals("cook", AbsorbedJournals.take(flag, cook).orElseThrow().getString("job"));
        assertEquals("farmer/wheat", AbsorbedJournals.take(flag, farmer).orElseThrow().getString("job"));
    }

    @Test
    void adoptedSnapshotsSurviveTheTownStateBeingRepublishedMidRespawn() {
        // Each respawned townie re-publishes the town state with only the townies spawned so far.
        CompoundTag flag = new CompoundTag();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        putTownState(flag, villagerTag(first, "cook"), villagerTag(second, "baker/bread"));
        AbsorbedJournals.adoptTownState(flag, TOWN_STATE);

        AbsorbedJournals.take(flag, first);
        putTownState(flag, villagerTag(first, "cook"));

        assertEquals("baker/bread", AbsorbedJournals.take(flag, second).orElseThrow().getString("job"));
    }

    @Test
    void aNewVisitorHasNoJournal() {
        CompoundTag flag = new CompoundTag();
        putTownState(flag, villagerTag(UUID.randomUUID(), "cook"));
        AbsorbedJournals.adoptTownState(flag, TOWN_STATE);

        assertTrue(AbsorbedJournals.take(flag, UUID.randomUUID()).isEmpty());
    }

    @Test
    void snapshotsLiveOnTheFlagTagSoTheyTravelWithAWholeTagCopy() {
        CompoundTag original = new CompoundTag();
        UUID baker = UUID.randomUUID();
        AbsorbedJournals.remember(original, baker, villagerTag(baker, "baker/bread"));

        CompoundTag relocated = new CompoundTag();
        for (String key : original.getAllKeys()) {
            relocated.put(key, original.get(key).copy());
        }

        assertEquals("baker/bread", AbsorbedJournals.take(relocated, baker).orElseThrow().getString("job"));
    }
}
