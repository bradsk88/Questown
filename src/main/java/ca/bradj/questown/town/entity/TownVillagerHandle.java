package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Effect;
import ca.bradj.questown.town.TownVillagerMoods;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.VillagerStatsData;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class TownVillagerHandle {

    static final TownVillagerHandlerSerializer SERIALIZER = new TownVillagerHandlerSerializer();
    final SimpleVillagerHandle<CompoundTag, VisitorMobEntity> delegate;
    final TownVillagerUIsHandle uis;
    final TownVillagerRenderingHandle rendering = new TownVillagerRenderingHandle();
    private final UnsafeTown town = new UnsafeTown(getClass());
    private final TownVillagerSleepModule sleep = new TownVillagerSleepModule();
    private final HealingModule<VisitorMobEntity> healing = new TownVillagerHealingModule();
    private final TownVillagerMoods moods = new TownVillagerMoods();
    final TownVillagerLearningHandle learning = new TownVillagerLearningHandle();

    TownVillagerHandle() {
        SimpleVillagerHandle.Configs config = new SimpleVillagerHandle.Configs(
                Compat.configGet(Config.BASE_FULLNESS).get(),
                Compat.configGet(Config.HUNGER_ENABLED).get(),
                Compat.configGet(Config.FLAG_TICK_INTERVAL).get(),
                Compat.configGet(Config.NORMAL_BED_HEAL_MULTIPLIER).get(),
                Compat.configGet(Config.EXPERIENCE_REQUIRED_AT_LEVEL_1).get(),
                Compat.configGet(Config.EXPERIENCE_RAMP_FACTOR).get(),
                Compat.configGet(Config.DAMAGE_TICKS).get(),
                Compat.configGet(Config.NEUTRAL_MOOD).get(),
                Compat.configGet(Config.BUFFER_TICKS_AFTER_FOOD_ATTEMPT).get(),
                Compat.configGet(Config.MAX_TICKS_BETWEEN_DOWNTIME).get()
        );
        this.delegate = new SimpleVillagerHandle<>(
                new Delegator<>() {
            @Override
            public JobID getJobId(VisitorMobEntity vEntity) {
                return vEntity.getJobId();
            }

            @Override
            public UUID getUUID(VisitorMobEntity v) {
                return v.getUUID();
            }

            @Override
            public void setJob(
                    UUID villagerUUID,
                    JobID newJob,
                    VisitorMobEntity f,
                    boolean announce
            ) {
                f.setJob(ServerJobsRegistry.getInitializedJob(
                        town.getServerLevelUnsafe(),
                        newJob,
                        f.getJobJournalSnapshot().items(),
                        villagerUUID
                ));
                @NotNull TownFlagBlockEntity t = town.getUnsafe();
                t.setChanged();
                if (announce) {
                    t.messages.jobChanged(newJob, villagerUUID);
                }
            }

            @Override
            public void setChanged() {
                town.getUnsafe().setChanged();
            }

            @Override
            public VillagerSleepModule<VisitorMobEntity> getSleepModule() {
                return sleep;
            }

            @Override
            public void debug(
                    QT.QTLogger villagerLogger,
                    String category,
                    String s,
                    Object... args
            ) {
                town.getUnsafe().getDebugLogger(villagerLogger, category).log(s, args);
            }

            @Override
            public boolean isUUID(
                    VisitorMobEntity v,
                    UUID ownerUUID
            ) {
                if (v.getVUID() == null) {
                    return false;
                }
                return v.getVUID().matches(ownerUUID);
            }

            @Override
            public void invalidateCachedWorkPossibilities() {
                town.getUnsafe().startableWork.invalidate();
            }

            @Override
            public HealingModule<VisitorMobEntity> getHealingModule() {
                return healing;
            }

            @Override
            public void addChangeListener(
                    VisitorMobEntity vEntity,
                    Runnable runnable
            ) {
                vEntity.addChangeListener(runnable::run);
            }

            @Override
            public void broadcastMessage(
                    String s,
                    Object... args
            ) {
                town.getUnsafe().messages.broadcastMessage(s, args);
            }

            @Override
            public ImmutableList<JobID> shuffle(ImmutableSet<JobID> jobIDS) {
                return Compat.shuffle(jobIDS, town.getServerLevelUnsafe());
            }

            @Override
            public Optional<JobID> getOvernightJobOverride() {
                return town.getUnsafe().getQuestHandle().overnightJobOverride();
            }

            @Override
            public void discardEntity(VisitorMobEntity visitorMobEntity) {
                visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
            }

            @Override
            public boolean isDining(VisitorMobEntity v) {
                return ServerJobsRegistry.isDining(v.getJobId());
            }

            @Override
            public boolean canStopWorkingAtAnyTime(VisitorMobEntity v) {
                return v.canStopWorkingAtAnyTime();
            }

            @Override
            public void freeze(
                    VisitorMobEntity v,
                    int ticks
            ) {
                v.freeze(ticks);
            }

            @Override
            public float getMood(UUID uuid) {
                return moods.getMood(uuid);
            }

            @Override
            public void unlockJob(
                    VisitorMobEntity v,
                    JobID newJob
            ) {
                learning.unlockJob(v.getUUID(), newJob);
            }

            @Override
            public ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs() {
                return learning.getUnlockedJobs();
            }

            @Override
            public ImmutableSet<JobID> getChildJobsKnownToExist(JobID jobId) {
                return learning.getChildJobsKnownToExist(jobId);
            }
        },
                config
        );
        this.uis = new TownVillagerUIsHandle(delegate);
    }

    public static void staticInit() {
        SimpleVillagerHandle.staticInit();
    }

    private static @Nullable UUID getUuid(VillagerUUID ownerVUID) {
        return VillagerUUID.get(ownerVUID);
    }

    private static @Nullable VillagerUUID fromUuid(UUID ownerVUID) {
        return VillagerUUID.from(ownerVUID);
    }

    void associate(TownFlagBlockEntity t) {
        this.town.initialize(t);
        this.learning.associate(t);
        delegate.associate(t);
        uis.init(t);
        sleep.associate(t);
        rendering.associate(t);
    }

    void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, ? extends ImmutableCollection<Effect>> moodEffects,
            Map<UUID, Integer> damage,
            Map<UUID, ? extends ImmutableCollection<JobID>> unlockedJobs,
            Map<UUID, ? extends Map<JobID, ? extends ImmutableCollection<JobID>>> jobsKnownToExist,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<VillagerUUID, Boolean> jobChangesPending
    ) {
        this.learning.initialize(unlockedJobs, jobsKnownToExist);
        delegate.initialize(
                fullness,
                damage,
                experience,
                level,
                UtilClean.mapKeys(jobChangesPending, TownVillagerHandle::getUuid),
                Config.HUNGER_ENABLED.get()
        );
        moods.initialize(moodEffects);
    }

    void recallVillagers() {
        final BlockPos visitorJoinPos = town.getUnsafe().getBlockPos();
        delegate.forEach(v -> {
            QT.FLAG_LOGGER.info("Moving {} to {} and healing", v, visitorJoinPos);
            v.setPos(visitorJoinPos.getX(), visitorJoinPos.getY(), visitorJoinPos.getZ());
            v.setHealth(v.getMaxHealth());
        });
    }

    void applyEffect(
            ResourceLocation effect,
            Long expireOnTick,
            UUID uuid
    ) {
        // TODO: Generalize
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER.equals(effect)) {
            delegate.fillHunger(uuid);
            return;
        }
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER_HALF.equals(effect)) {
            delegate.fillHunger(uuid, 0.5f);
            return;
        }
        moods.tryApplyEffect(effect, expireOnTick, uuid);
    }

    ImmutableMap<UUID, Integer> getFullness() {
        return ImmutableMap.copyOf(delegate.fullness);
    }

    ImmutableMap<UUID, ImmutableList<Effect>> getMoodEffects() {
        return ImmutableMap.copyOf(moods.getEffects());
    }

    ImmutableMap<UUID, Integer> getDamage() {
        return ImmutableMap.copyOf(delegate.damage);
    }

    ImmutableMap<UUID, Integer> getExperience() {
        return ImmutableMap.copyOf(delegate.experience);
    }

    ImmutableMap<UUID, Integer> getLevels() {
        return ImmutableMap.copyOf(delegate.levels);
    }

    ImmutableMap<UUID, Map<JobID, ? extends Collection<JobID>>> getJobsKnownToExist() {
        return ImmutableMap.copyOf(learning.jobsKnownToExist);
    }

    long getTick() {
        return Util.getTick(town.getServerLevelUnsafe());
    }

    ImmutableMap<VillagerUUID, Boolean> blockOfProgressMap() {
        return UtilClean.mapKeys(delegate.hasBlockOfProgress, TownVillagerHandle::fromUuid);
    }

    void handleMorning(Long newTime) {
        delegate.handleMorning(newTime);
    }

    Stream<VisitorMobEntity> stream() {
        return delegate.stream();
    }

    void changeJobForVillager(
            VillagerUUID uuid,
            JobID idForRoot,
            boolean b
    ) {
        delegate.changeJobForVillager(getUuid(uuid), idForRoot, getTick(), b);
    }

    VisitorMobEntity getEntity(VillagerUUID ownerUUID) {
        return delegate.getEntity(getUuid(ownerUUID));
    }

    float getDamagePercent(VillagerUUID ownerVUID) {
        return delegate.getDamagePercent(getUuid(ownerVUID));
    }

    boolean hasBlockOfProgress(VillagerUUID ownerVUID) {
        return delegate.hasBlockOfProgress(getUuid(ownerVUID));
    }

    boolean isReadyForDowntime(
            VillagerUUID ownerVUID,
            long tick
    ) {
        return delegate.isReadyForDowntime(getUuid(ownerVUID), tick);
    }

    long size() {
        return delegate.size();
    }

    void tick(
            long tick,
            Signals signals
    ) {
        moods.tick(tick);
        learning.tick(tick);
        delegate.tick(signals);
    }

    Map<VillagerUUID, Boolean> getJobChangesPending() {
        return UtilClean.mapKeys(delegate.getJobChangesPending(), TownVillagerHandle::fromUuid);
    }

    void register(VisitorMobEntity visitorMobEntity) {
        delegate.register(visitorMobEntity);

        ImmutableList<JobID> defaultWork = ServerJobsRegistry.getDefaultWork(visitorMobEntity.getJobId());
        for (JobID jobID : defaultWork) {
            learning.unlockJob(visitorMobEntity.getUUID(), jobID);
        }
        learning.requestKnowledge(visitorMobEntity.getUUID(), defaultWork);
    }

    void addHungryListener(Consumer<VisitorMobEntity> o) {
        delegate.addHungryListener(o);
    }

    void addStatsListener(Consumer<VillagerStatsData> o) {
        delegate.addStatsListener(o);
    }

    ImmutableMap<VillagerUUID, ImmutableSet<JobID>> getUnlockedJobs() {
        return UtilClean.mapKeys(learning.getUnlockedJobs(), TownVillagerHandle::fromUuid);
    }

    UnsafeVillagerData getUnprotectedDataHandle(@Nullable UUID vuid) {
        return new UnsafeVillagerData() {
            public String get(String key) {
                CompoundTag tag = UtilClean.getOrDefault(delegate.customData, vuid, new CompoundTag());
                if (!tag.contains(key)) {
                    return null;
                }
                return tag.getString(key);
            }

            public void write(
                    String key,
                    String value
            ) {

                CompoundTag tag = UtilClean.getOrDefault(delegate.customData, vuid, new CompoundTag());
                tag.putString(key, value);
                delegate.customData.put(vuid, tag);
            }

            public void clear(String key) {
                CompoundTag tag = UtilClean.getOrDefault(delegate.customData, vuid, new CompoundTag());
                tag.remove(key);
                delegate.customData.put(vuid, tag);
            }
        };
    }

    public boolean isDining(UUID uuid) {
        return delegate.isDining(uuid);
    }

    public boolean canDine(UUID uuid) {
        return delegate.canDine(uuid);
    }

    public boolean gaveUpDiningRecently(@Nullable VillagerUUID vuid) {
        return delegate.gaveUpDiningRecently(getUuid(vuid), getTick());
    }
}
