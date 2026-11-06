package ca.bradj.questown.town.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The saved state of villagers taken into the flag when a town shuts down, so each comes back with its job and items. Kept on the flag, so it moves with the town. */
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

    /** Saves every villager in the town state that has no saved state yet. Run before the respawns begin. */
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

    /** The villagers saved and not yet back. */
    static List<UUID> absorbed(CompoundTag flagTag) {
        return flagTag.getCompound(NBT_ABSORBED_JOURNALS).getAllKeys().stream()
                .map(UUID::fromString)
                .toList();
    }

    /** Removes and returns the villager's saved state; empty for a new villager. */
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
