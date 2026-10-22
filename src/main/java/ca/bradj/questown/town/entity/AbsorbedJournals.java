package ca.bradj.questown.town.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The journals of townies absorbed into the flag by a shutdown ritual (ADR-0009), keyed by UUID on
 * the flag's persistent tag, so a respawn (wake, cancel, or relocation placement) brings each
 * townie back with its job, status and carried items instead of a fresh gatherer. Living on the
 * flag tag means the snapshots travel with the relocation's whole-blob copy and survive a restart.
 *
 * <p>Holds the serialized villager tag only ({@code TownStateSerializer.storeVillager} format); the
 * caller encodes and decodes it.
 */
final class AbsorbedJournals {

    static final String NBT_ABSORBED_JOURNALS = "absorbed_journals";

    private AbsorbedJournals() {
    }

    static void remember(
            CompoundTag flagTag,
            UUID villager,
            CompoundTag villagerTag
    ) {
        CompoundTag journals = flagTag.getCompound(NBT_ABSORBED_JOURNALS);
        journals.put(villager.toString(), villagerTag);
        flagTag.put(NBT_ABSORBED_JOURNALS, journals);
    }

    /**
     * Snapshot every townie in the stored town state that has no absorb-time snapshot yet. For a
     * flag that went dormant without absorbing its townies (a town shut down before snapshots
     * existed). Must run before the respawn starts: each respawned townie re-publishes the town
     * state, which drops the entries of townies not yet respawned.
     */
    static void adoptTownState(
            CompoundTag flagTag,
            String townStateKey
    ) {
        CompoundTag journals = flagTag.getCompound(NBT_ABSORBED_JOURNALS);
        ListTag villagers = flagTag.getCompound(townStateKey).getList("villagers", Tag.TAG_COMPOUND);
        for (Tag t : villagers) {
            CompoundTag v = (CompoundTag) t;
            if (!v.hasUUID("UUID")) {
                continue;
            }
            String key = v.getUUID("UUID").toString();
            if (!journals.contains(key, Tag.TAG_COMPOUND)) {
                journals.put(key, v.copy());
            }
        }
        flagTag.put(NBT_ABSORBED_JOURNALS, journals);
    }

    /** The townies with a snapshot waiting: the ones absorbed and not yet respawned. */
    static List<UUID> absorbed(CompoundTag flagTag) {
        return flagTag.getCompound(NBT_ABSORBED_JOURNALS).getAllKeys().stream()
                .map(UUID::fromString)
                .toList();
    }

    /** Remove and return {@code villager}'s snapshot; empty for a genuinely new visitor. */
    static Optional<CompoundTag> take(
            CompoundTag flagTag,
            UUID villager
    ) {
        CompoundTag journals = flagTag.getCompound(NBT_ABSORBED_JOURNALS);
        String key = villager.toString();
        if (!journals.contains(key, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag snapshot = journals.getCompound(key);
        journals.remove(key);
        flagTag.put(NBT_ABSORBED_JOURNALS, journals);
        return Optional.of(snapshot);
    }
}
