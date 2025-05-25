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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class TownVillagerHandlerSerializer {
    private static final String NBT_FULLNESS = "fullness";
    private static final String NBT_EXP = "experience";
    private static final String NBT_LEVELS = "level";
    private static final String NBT_MOOD = "mood";
    private static final String NBT_MOOD_EFFECTS = "mood_effects";
    private static final String NBT_VILLAGER_ID = "villager_uuid";
    private static final String NBT_UNLOCKED_JOBS = "unlocked_jobs";
    private static final String NBT_VALUE = "value";
    private static final String NBT_DURATION = "duration";
    private static final String NBT_DAMAGE = "damage";

    public void deserialize(CompoundTag compound, TownVillagerHandle villagerHandle, long currentTick) {
        Function<Tag, Integer> simpleInt = t -> ((CompoundTag) t).getInt(NBT_VALUE);
        ImmutableMap<UUID, Integer> fullness = deserializeMap(compound, NBT_FULLNESS, simpleInt);
        ImmutableMap<UUID, ImmutableList<Effect>> moodEffects = deserializeMap(compound, NBT_MOOD_EFFECTS, t -> {
            ListTag effects = ((CompoundTag) t).getList(NBT_MOOD_EFFECTS, Tag.TAG_COMPOUND);
            ImmutableList.Builder<Effect> b2 = ImmutableList.builder();

            effects.forEach(meTag -> b2.add(new Effect(new ResourceLocation(((CompoundTag) meTag).getString(NBT_VALUE)), currentTick + ((CompoundTag) meTag).getLong(NBT_DURATION))));
            return b2.build();
        });

        ImmutableMap<UUID, Integer> damage = deserializeMap(compound, NBT_DAMAGE, simpleInt);

        ImmutableMap<UUID, ImmutableList<JobID>> unlockedJobs = deserializeMap(compound, NBT_UNLOCKED_JOBS, t -> {
            ListTag jobs = ((CompoundTag) t).getList(NBT_VALUE, Tag.TAG_COMPOUND);

            List<JobID> list = jobs.stream().map(v -> JobID.fromTag((CompoundTag) v)).toList();
            return ImmutableList.copyOf(list);
        });

        ImmutableMap<UUID, Integer> experience = deserializeMap(compound, NBT_EXP, simpleInt);
        ImmutableMap<UUID, Integer> level = deserializeMap(compound, NBT_LEVELS, simpleInt);

        villagerHandle.initialize(fullness, moodEffects, damage, unlockedJobs, experience, level);
    }

    private <X> ImmutableMap<UUID, X> deserializeMap(CompoundTag compound, String tagId, Function<Tag, X> fn) {
        ImmutableMap.Builder<UUID, X> mb = ImmutableMap.builder();
        ListTag fullnessPairs = compound.getList(tagId, Tag.TAG_COMPOUND);

        fullnessPairs.forEach(tag -> {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            mb.put(uuid, fn.apply(tag));
        });
        return mb.build();
    }

    public CompoundTag serialize(TownVillagerHandle villagerHandle, long currentTick) {
        BiConsumer<CompoundTag, Integer> simpleInt = (t, v) -> {
            t.putInt(NBT_VALUE, v);
        };

        CompoundTag compound = new CompoundTag();

        serializeMap(compound, NBT_FULLNESS, villagerHandle.fullness, simpleInt);
        serializeMap(compound, NBT_MOOD_EFFECTS, villagerHandle.moods.getEffects(), (t, v) -> {
            ListTag effectsList = new ListTag();
            v.forEach(effect -> {
                CompoundTag effectTag = new CompoundTag();
                effectTag.putString(NBT_VALUE, effect.effect().toString());
                effectTag.putLong(NBT_DURATION, Math.max(effect.untilTick() - currentTick, 0));
                effectsList.add(effectTag);
            });
            t.put(NBT_MOOD_EFFECTS, effectsList);
        });
        serializeMap(compound, NBT_DAMAGE, villagerHandle.damage, simpleInt);
        serializeMap(compound, NBT_EXP, villagerHandle.experience, simpleInt);
        serializeMap(compound, NBT_LEVELS, villagerHandle.levels, simpleInt);
        serializeMap(compound, NBT_UNLOCKED_JOBS, villagerHandle.unlockedJobs, (CompoundTag t, Collection<JobID> j) -> {
            ListTag lt = new ListTag();
            for (JobID jobID : j) {
                lt.add(JobID.toTag(jobID));
            }
            t.put(NBT_VALUE, lt);
        });

        return compound;
    }

    private <X> void serializeMap(CompoundTag compound, String id, Map<UUID, X> fullnessMap, BiConsumer<CompoundTag, ? super X> writer) {
        ListTag pairs = new ListTag();

        fullnessMap.forEach((uuid, value) -> {
            CompoundTag tag = new CompoundTag();
            writer.accept(tag, value);
            tag.putUUID(NBT_VILLAGER_ID, uuid);
            pairs.add(tag);
        });

        compound.put(id, pairs);
    }
}
