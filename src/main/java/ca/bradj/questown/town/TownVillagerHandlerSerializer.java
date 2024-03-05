package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Map;
import java.util.UUID;

public class TownVillagerHandlerSerializer {
    private static final String NBT_FULLNESS_SIZE = "fullness_size";
    private static final String NBT_FULLNESS = "fullness";
    private static final String NBT_MOOD_SIZE = "mood_size";
    private static final String NBT_MOOD = "mood";
    private static final String NBT_MOOD_EFFECTS = "mood_effects";
    private static final String NBT_MOOD_EFFECT_SIZE = "mood_effects_size";
    private static final String NBT_VILLAGER_ID = "villager_uuid";
    private static final String NBT_VALUE = "value";
    private static final String NBT_DURATION = "duration";

    public void deserialize(CompoundTag compound, TownFlagBlockEntity t, TownVillagerHandle villagerHandle) {
        return;

        // FIXME: Figure out why this seems to be zeroing out fullness and mood
//        ImmutableMap.Builder<UUID, Integer> fullness = ImmutableMap.builder();
//        ImmutableMap.Builder<UUID, ImmutableList<Effect>> moodEffects = ImmutableMap.builder();
//
//        int fSize = compound.getInt(NBT_FULLNESS_SIZE);
//        ListTag fullnessPairs = compound.getList(NBT_FULLNESS, fSize);
//
//        fullnessPairs.forEach(tag -> {
//            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
//            int value = ((CompoundTag) tag).getInt(NBT_VALUE);
//            fullness.put(uuid, value);
//        });
//
//        int mSize = compound.getInt(NBT_MOOD_SIZE);
//        ListTag moodPairs = compound.getList(NBT_MOOD, mSize);
//
//        moodPairs.forEach(tag -> {
//            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
//            int meSize = compound.getInt(NBT_MOOD_EFFECT_SIZE);
//            ListTag effects = ((CompoundTag) tag).getList(NBT_MOOD_EFFECTS, meSize);
//            ImmutableList.Builder<Effect> b2 = ImmutableList.builder();
//
//            effects.forEach(meTag -> {
//                b2.add(new Effect(
//                        new ResourceLocation(((CompoundTag) meTag).getString(NBT_VALUE)),
//                        ((CompoundTag) meTag).getLong(NBT_DURATION)
//                ));
//            });
//
//            moodEffects.put(uuid, b2.build());
//        });
//
//        villagerHandle.initialize(fullness.build(), moodEffects.build());
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

        compound.putInt(NBT_FULLNESS_SIZE, fullnessMap.size());
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

            tag.putInt(NBT_MOOD_EFFECT_SIZE, effects.size());
            tag.put(NBT_MOOD_EFFECTS, effectsList);
            moodPairs.add(tag);
        });

        compound.putInt(NBT_MOOD_SIZE, moodEffectsMap.size());
        compound.put(NBT_MOOD, moodPairs);

        return compound;
    }
}
