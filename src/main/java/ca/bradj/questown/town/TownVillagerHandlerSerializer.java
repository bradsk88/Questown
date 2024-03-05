package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public class TownVillagerHandlerSerializer {
    private static final String NBT_FULLNESS = "fullness";
    private static final String NBT_MOOD = "mood";
    private static final String NBT_MOOD_EFFECTS = "mood_effects";
    private static final String NBT_VILLAGER_ID = "villager_uuid";
    private static final String NBT_VALUE = "value";
    private static final String NBT_DURATION = "duration";

    public void deserialize(CompoundTag compound, TownVillagerHandle villagerHandle, long currentTick) {
        ImmutableMap.Builder<UUID, Integer> fullness = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, ImmutableList<Effect>> moodEffects = ImmutableMap.builder();

        ListTag fullnessPairs = compound.getList(NBT_FULLNESS, Tag.TAG_COMPOUND);

        fullnessPairs.forEach(tag -> {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            int value = ((CompoundTag) tag).getInt(NBT_VALUE);
            fullness.put(uuid, value);
        });

        ListTag moodPairs = compound.getList(NBT_MOOD, Tag.TAG_COMPOUND);

        moodPairs.forEach(tag -> {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            ListTag effects = ((CompoundTag) tag).getList(NBT_MOOD_EFFECTS, Tag.TAG_COMPOUND);
            ImmutableList.Builder<Effect> b2 = ImmutableList.builder();

            effects.forEach(meTag -> b2.add(new Effect(
                    new ResourceLocation(((CompoundTag) meTag).getString(NBT_VALUE)),
                    currentTick + ((CompoundTag) meTag).getLong(NBT_DURATION)
            )));

            moodEffects.put(uuid, b2.build());
        });

        villagerHandle.initialize(fullness.build(), moodEffects.build());
    }

    public CompoundTag serialize(TownVillagerHandle villagerHandle, long currentTick) {
        CompoundTag compound = new CompoundTag();

        Map<UUID, Integer> fullnessMap = villagerHandle.fullness;
        ListTag fullnessPairs = new ListTag();

        fullnessMap.forEach((uuid, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(NBT_VILLAGER_ID, uuid);
            tag.putInt(NBT_VALUE, value);
            fullnessPairs.add(tag);
        });

        compound.put(NBT_FULLNESS, fullnessPairs);

        ImmutableMap<UUID, ImmutableList<Effect>> moodEffectsMap = villagerHandle.moods.getEffects();

        ListTag moodPairs = new ListTag();
        moodEffectsMap.forEach((uuid, effects) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(NBT_VILLAGER_ID, uuid);

            ListTag effectsList = new ListTag();
            effects.forEach(effect -> {
                CompoundTag effectTag = new CompoundTag();
                effectTag.putString(NBT_VALUE, effect.effect().toString());
                effectTag.putLong(NBT_DURATION, Math.max(effect.untilTick() - currentTick, 0));
                effectsList.add(effectTag);
            });

            tag.put(NBT_MOOD_EFFECTS, effectsList);
            moodPairs.add(tag);
        });

        compound.put(NBT_MOOD, moodPairs);

        return compound;
    }
}
