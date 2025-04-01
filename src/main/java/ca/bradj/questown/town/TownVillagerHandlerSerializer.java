package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TownVillagerHandlerSerializer {
    private static final String NBT_FULLNESS = "fullness";
    private static final String NBT_MOOD = "mood";
    private static final String NBT_MOOD_EFFECTS = "mood_effects";
    private static final String NBT_VILLAGER_ID = "villager_uuid";
    private static final String NBT_UNLOCKED_JOBS = "unlocked_jobs";
    private static final String NBT_VALUE = "value";
    private static final String NBT_DURATION = "duration";
    private static final String NBT_DAMAGE = "damage";

    public void deserialize(
            CompoundTag compound,
            TownVillagerHandle villagerHandle,
            long currentTick
    ) {
        ImmutableMap.Builder<UUID, Integer> fullness = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, ImmutableList<Effect>> moodEffects = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, Integer> damage = ImmutableMap.builder();
        ImmutableMap.Builder<UUID, ImmutableList<JobID>> unlockedJobs = ImmutableMap.builder();

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


        ListTag damagePairs = compound.getList(NBT_DAMAGE, Tag.TAG_COMPOUND);

        damagePairs.forEach(tag -> {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            int value = ((CompoundTag) tag).getInt(NBT_VALUE);
            damage.put(uuid, value);
        });

        ListTag unlockedJobsPairs = compound.getList(NBT_UNLOCKED_JOBS, Tag.TAG_COMPOUND);
        for (Tag tag : unlockedJobsPairs) {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            ListTag jobs = ((CompoundTag) tag).getList(NBT_VALUE, Tag.TAG_COMPOUND);

            List<JobID> list = jobs.stream().map(v -> JobID.fromTag((CompoundTag) v)).toList();
            unlockedJobs.put(uuid, ImmutableList.copyOf(list));
        }

        villagerHandle.initialize(fullness.build(), moodEffects.build(), damage.build(), unlockedJobs.build());
    }

    public CompoundTag serialize(
            TownVillagerHandle villagerHandle,
            long currentTick
    ) {
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


        Map<UUID, Integer> damageMap = villagerHandle.damage;
        ListTag damagePairs = new ListTag();

        damageMap.forEach((uuid, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(NBT_VILLAGER_ID, uuid);
            tag.putInt(NBT_VALUE, value);
            damagePairs.add(tag);
        });

        compound.put(NBT_DAMAGE, damagePairs);

        Map<UUID, Collection<JobID>> unlockedMap = villagerHandle.unlockedJobs;
        ListTag unlockedPairs = new ListTag();

        unlockedMap.forEach((uuid, value) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(NBT_VILLAGER_ID, uuid);
            ListTag lt = new ListTag();
            for (JobID jobID : value) {
                lt.add(JobID.toTag(jobID));
            }
            tag.put(NBT_VALUE, lt);
            unlockedPairs.add(tag);
        });

        compound.put(NBT_UNLOCKED_JOBS, unlockedPairs);

        return compound;
    }
}
