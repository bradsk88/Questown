package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.Effect;
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
import java.util.function.Function;

public class TownVillagerHandlerSerializer {
    private static final String NBT_FULLNESS = "fullness";
    private static final String NBT_EXP = "experience";
    private static final String NBT_LEVELS = "level";
    private static final String NBT_MOOD = "mood";
    private static final String NBT_MOOD_EFFECTS = "mood_effects";
    private static final String NBT_VILLAGER_ID = "villager_uuid";
    private static final String NBT_UNLOCKED_JOBS = "unlocked_jobs";
    private static final String NBT_JOBS_KNOWN_TO_EXIST = "jobs_known_to_exist";
    private static final String NBT_JOB_ID = "job_id";
    private static final String NBT_VALUE = "value";
    private static final String NBT_DURATION = "duration";
    private static final String NBT_DAMAGE = "damage";
    private static final String NBT_PENDING_JOB_CHANGES = "pending_job_changes";
    private static final String NBT_PROFICIENCIES = "proficiencies";

    public void deserialize(
            CompoundTag compound,
            TownVillagerHandle villagerHandle,
            long currentTick
    ) {
        Function<Tag, Integer> simpleInt = t -> ((CompoundTag) t).getInt(NBT_VALUE);
        Function<Tag, Boolean> simpleBool = t -> ((CompoundTag) t).getBoolean(NBT_VALUE);
        ImmutableMap<UUID, Integer> fullness = deserializeMap(compound, NBT_FULLNESS, simpleInt);
        ImmutableMap<UUID, ImmutableList<Effect>> moodEffects = deserializeMap(
                compound, NBT_MOOD_EFFECTS, t -> {
                    ListTag effects = ((CompoundTag) t).getList(NBT_MOOD_EFFECTS, Tag.TAG_COMPOUND);
                    ImmutableList.Builder<Effect> b2 = ImmutableList.builder();

                    effects.forEach(meTag -> b2.add(new Effect(
                            new ResourceLocation(((CompoundTag) meTag).getString(NBT_VALUE)),
                            currentTick + ((CompoundTag) meTag).getLong(NBT_DURATION)
                    )));
                    return b2.build();
                }
        );

        ImmutableMap<UUID, Integer> damage = deserializeMap(compound, NBT_DAMAGE, simpleInt);

        ImmutableMap<UUID, ImmutableList<JobID>> unlockedJobs = deserializeMap(
                compound, NBT_UNLOCKED_JOBS, t -> {
                    ListTag jobs = ((CompoundTag) t).getList(NBT_VALUE, Tag.TAG_COMPOUND);

                    List<JobID> list = jobs.stream().map(v -> JobID.fromTag((CompoundTag) v)).toList();
                    return ImmutableList.copyOf(list);
                }
        );
        ImmutableMap<UUID, ImmutableMap<JobID, ImmutableList<JobID>>> knownJobs = deserializeMap(
                compound, NBT_VALUE, ttt -> {
                    ImmutableMap.Builder<JobID, ImmutableList<JobID>> bbb = ImmutableMap.builder();
                    ListTag l = ((CompoundTag) ttt).getList(NBT_VALUE, Tag.TAG_COMPOUND);
                    l.forEach(tttt -> {
                        CompoundTag jobId = ((CompoundTag) tttt).getCompound(NBT_JOB_ID);
                        ListTag jobz = ((CompoundTag) tttt).getList(NBT_VALUE, Tag.TAG_COMPOUND);
                        ImmutableList.Builder<JobID> jb = ImmutableList.builder();
                        jobz.forEach(j -> jb.add(JobID.fromTag((CompoundTag) j)));
                        bbb.put(JobID.fromTag(jobId), jb.build());
                    });
                    return bbb.build();
                }
        );

        ImmutableMap<UUID, Integer> experience = deserializeMap(compound, NBT_EXP, simpleInt);
        ImmutableMap<UUID, Integer> level = deserializeMap(compound, NBT_LEVELS, simpleInt);
        ImmutableMap<VillagerUUID, Boolean> pending = deserializeVMap(compound, NBT_PENDING_JOB_CHANGES, simpleBool);
        ImmutableMap<UUID, ImmutableMap<String, Float>> proficiencies = deserializeMap(
                compound, NBT_PROFICIENCIES, t -> {
                    CompoundTag pt = ((CompoundTag) t).getCompound(NBT_PROFICIENCIES);
                    ImmutableMap.Builder<String, Float> b2 = ImmutableMap.builder();
                    for (String key : pt.getAllKeys()) {
                        b2.put(key, pt.getFloat(key));
                    }
                    return b2.build();
                }
        );

        villagerHandle.initialize(
                fullness, moodEffects, damage, unlockedJobs, knownJobs, experience, level, pending, proficiencies
        );
    }

    private <X> ImmutableMap<UUID, X> deserializeMap(
            CompoundTag compound,
            String tagId,
            Function<Tag, X> fn
    ) {
        ImmutableMap.Builder<UUID, X> mb = ImmutableMap.builder();
        ListTag fullnessPairs = compound.getList(tagId, Tag.TAG_COMPOUND);

        fullnessPairs.forEach(tag -> {
            UUID uuid = ((CompoundTag) tag).getUUID(NBT_VILLAGER_ID);
            mb.put(uuid, fn.apply(tag));
        });
        return mb.build();
    }

    private <X> ImmutableMap<VillagerUUID, X> deserializeVMap(
            CompoundTag compound,
            String tagId,
            Function<Tag, X> fn
    ) {
        ImmutableMap.Builder<VillagerUUID, X> mb = ImmutableMap.builder();
        ListTag fullnessPairs = compound.getList(tagId, Tag.TAG_COMPOUND);

        fullnessPairs.forEach(tag -> {
            VillagerUUID uuid = VillagerUUID.fromNBT((CompoundTag) tag, NBT_VILLAGER_ID);
            mb.put(uuid, fn.apply(tag));
        });
        return mb.build();
    }

    private <K, X> ImmutableMap<K, X> deserializeMap(
            CompoundTag compound,
            String tagId,
            Function<Tag, X> fn,
            Function<Tag, K> keyFn
    ) {
        ImmutableMap.Builder<K, X> mb = ImmutableMap.builder();
        ListTag fullnessPairs = compound.getList(tagId, Tag.TAG_COMPOUND);

        fullnessPairs.forEach(tag -> {
            K uuid = keyFn.apply(tag);
            mb.put(uuid, fn.apply(tag));
        });
        return mb.build();
    }

    public CompoundTag serialize(
            TownVillagerHandle villagerHandle,
            long currentTick
    ) {
        BiConsumer<CompoundTag, Integer> simpleInt = (t, v) -> {
            t.putInt(NBT_VALUE, v);
        };
        BiConsumer<CompoundTag, Boolean> simpleBool = (t, v) -> {
            t.putBoolean(NBT_VALUE, v);
        };

        CompoundTag compound = new CompoundTag();

        serializeMap(compound, NBT_FULLNESS, villagerHandle.getFullness(), simpleInt);
        serializeMap(
                compound, NBT_MOOD_EFFECTS, villagerHandle.getMoodEffects(), (t, v) -> {
                    ListTag effectsList = new ListTag();
                    v.forEach(effect -> {
                        CompoundTag effectTag = new CompoundTag();
                        effectTag.putString(NBT_VALUE, effect.effect().toString());
                        effectTag.putLong(NBT_DURATION, Math.max(effect.untilTick() - currentTick, 0));
                        effectsList.add(effectTag);
                    });
                    t.put(NBT_MOOD_EFFECTS, effectsList);
                }
        );
        serializeMap(compound, NBT_DAMAGE, villagerHandle.getDamage(), simpleInt);
        serializeMap(compound, NBT_EXP, villagerHandle.getExperience(), simpleInt);
        serializeMap(compound, NBT_LEVELS, villagerHandle.getLevels(), simpleInt);
        serializeVMap(
                compound,
                NBT_UNLOCKED_JOBS,
                villagerHandle.getUnlockedJobs(),
                (CompoundTag t, Collection<JobID> j) -> t.put(NBT_VALUE, JobID.toTag(j))
        );
        BiConsumer<CompoundTag, Map<JobID, ? extends Collection<JobID>>> bc = (compoundTag, jobIDSetMap) -> {
            CompoundTag compound1 = new CompoundTag();
            serializeMap(
                    compound1,
                    NBT_VALUE,
                    jobIDSetMap,
                    this::serializeJobIdsValue,
                    (t, k) -> t.put(NBT_JOB_ID, JobID.toTag(k))
            );
            compoundTag.put(NBT_VALUE, compound1);
        };
        serializeMap(compound, NBT_JOBS_KNOWN_TO_EXIST, villagerHandle.getJobsKnownToExist(), bc);
        serializeVMap(compound, NBT_PENDING_JOB_CHANGES, villagerHandle.getJobChangesPending(), simpleBool);
        serializeMap(
                compound, NBT_PROFICIENCIES, villagerHandle.getAllProficiencies(), (t, m) -> {
                    CompoundTag pt = new CompoundTag();
                    m.forEach(pt::putFloat);
                    t.put(NBT_PROFICIENCIES, pt);
                }
        );
        return compound;
    }

    private void serializeJobIdsValue(
            CompoundTag compoundTag,
            Collection<JobID> jobIDS
    ) {
        compoundTag.put(NBT_VALUE, JobID.toTag(jobIDS));
    }

    private <X> void serializeMap(
            CompoundTag compound,
            String id,
            Map<UUID, X> fullnessMap,
            BiConsumer<CompoundTag, ? super X> writer
    ) {
        serializeMap(compound, id, fullnessMap, writer, (t, uuid) -> t.putUUID(NBT_VILLAGER_ID, uuid));
    }

    private <X> void serializeVMap(
            CompoundTag compound,
            String id,
            Map<VillagerUUID, X> fullnessMap,
            BiConsumer<CompoundTag, ? super X> writer
    ) {
        serializeMap(compound, id, fullnessMap, writer, (t, uuid) -> uuid.writeToNBT(t, NBT_VILLAGER_ID));
    }

    private <K, X> void serializeMap(
            CompoundTag compound,
            String id,
            Map<K, X> fullnessMap,
            BiConsumer<CompoundTag, ? super X> writer,
            BiConsumer<CompoundTag, K> writeKey
    ) {
        ListTag pairs = new ListTag();

        fullnessMap.forEach((uuid, value) -> {
            CompoundTag tag = new CompoundTag();
            writer.accept(tag, value);
            writeKey.accept(tag, uuid);
            pairs.add(tag);
        });

        compound.put(id, pairs);
    }
}
