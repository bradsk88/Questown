package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final ca.bradj.questown.town.TownVillagerProficiencies proficiencies =
            new ca.bradj.questown.town.TownVillagerProficiencies();
    final TownVillagerLearningHandle learning = new TownVillagerLearningHandle();
    /**
     * Display numbers per townie ("Villager N"): assigned on arrival, persisted with the
     * handle, and never re-issued. 2026-10-14 playtest ruling: no reuse.
     */
    private final VillagerDisplayNumbers displayNumbers = new VillagerDisplayNumbers();

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
                Compat.configGet(Config.MAX_TICKS_BETWEEN_DOWNTIME).get(),
                Compat.configGet(Config.MOOD_EFFECT_DURATION_SLEEPLESS).get()
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
            public String displayNameFor(UUID vuid) {
                return TownVillagerHandle.this.displayNameFor(vuid);
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
                    String label = displayNameFor(villagerUUID);
                    t.messages.jobChanged(newJob, label.isEmpty() ? ca.bradj.questown.core.UtilClean.truncateMiddle(villagerUUID) : label);
                }
            }

            @Override
            public void setChanged() {
                if (town.isInitialized()) {
                    town.getUnsafe().setChanged();
                }
                // Load-time registration (SimpleVillagerHandle.register's tail
                // call) arrives before associate(); the town state was just
                // read from this very block entity, so nothing new needs
                // persisting. Throwing here made vanilla drop the loading
                // entity - the second landmine behind the claimBed one,
                // masked until the first was fixed (2026-10-14).
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
                town.getUnsafe().possibleWork.invalidate();
            }

            @Override
            public void resetWorkStatus(UUID villagerUUID) {
                town.getUnsafe().resetWorkStatusFor(villagerUUID);
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
            public boolean isRemoved(VisitorMobEntity e) {
                return e.isRemoved();
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
            public ImmutableList<String> getMoodEffectIds(UUID uuid) {
                return moods.getEffects().getOrDefault(uuid, ImmutableList.of()).stream()
                        .map(e -> e.effect().toString())
                        .distinct()
                        .collect(ImmutableList.toImmutableList());
            }

            @Override
            public void applyMoodEffect(UUID uuid, ResourceLocation effect, long untilTick) {
                moods.tryApplyEffect(effect, untilTick, uuid);
            }

            @Override
            public void unlockJob(
                    VisitorMobEntity v,
                    JobID newJob
            ) {
                learning.unlockJob(v.getUUID(), newJob);
            }

            @Override
            public ImmutableList<JobID> getDefaultWork(JobID root) {
                return ServerJobsRegistry.getDefaultWork(root);
            }

            @Override
            public void postStarterRequests(JobID root) {
                town.getUnsafe().postStarterRequests(root);
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
            ImmutableMap<VillagerUUID, Boolean> jobChangesPending,
            Map<UUID, ? extends Map<String, Float>> proficiencies,
            ImmutableMap<UUID, JobID> guaranteedRoots,
            ImmutableMap<UUID, JobID> preRolls,
            Map<UUID, Integer> displayNumbersIn,
            int highestDisplayNumberIn
    ) {
        this.learning.initialize(unlockedJobs, jobsKnownToExist);
        delegate.initialize(
                fullness,
                damage,
                experience,
                level,
                UtilClean.mapKeys(jobChangesPending, TownVillagerHandle::getUuid),
                Config.HUNGER_ENABLED.get(),
                guaranteedRoots,
                preRolls
        );
        moods.initialize(moodEffects);
        this.proficiencies.initialize(proficiencies);
        this.displayNumbers.restore(displayNumbersIn, highestDisplayNumberIn);
    }

    /** The townie's display number, or 0 if none assigned yet. */
    public int displayNumberFor(UUID vuid) {
        return displayNumbers.numberFor(vuid);
    }

    /**
     * Assign the next display number (monotonic, never re-issued). Idempotent: a townie
     * that already has a number (reload from NBT) keeps it.
     */
    public void assignDisplayNumber(UUID vuid) {
        displayNumbers.assign(vuid);
    }

    /** The townie died: drop it from the saved roster (its number is never re-issued). */
    void retireDisplayNumber(UUID vuid) {
        displayNumbers.retire(vuid);
    }

    /** "Villager N" (the user-facing display name); the raw root as a last resort. */
    public String displayNameFor(UUID vuid) {
        int n = displayNumberFor(vuid);
        if (n <= 0) {
            return "";
        }
        return VillagerDisplayNumbers.nameplate(n).getString();
    }

    /**
     * Every assigned display name (villager UUID -&gt; "Villager N"); townies without a number
     * are omitted, so callers fall back to the truncated UUID. Feeds the flag quest-card
     * tooltips (the 2026-10-14 display-name work deferred this surface pending this map).
     */
    public ImmutableMap<UUID, String> displayNamesForAll() {
        ImmutableMap.Builder<UUID, String> b = ImmutableMap.builder();
        for (Map.Entry<UUID, Integer> e : displayNumbers.byVuid().entrySet()) {
            String name = displayNameFor(e.getKey());
            if (!name.isEmpty()) {
                b.put(e.getKey(), name);
            }
        }
        return b.build();
    }

    Map<UUID, Integer> getDisplayNumbers() {
        return displayNumbers.byVuid();
    }

    /**
     * Villagers in the town per the saved roster (one display number per townie, retired only
     * when it leaves or dies) - unlike {@link #size()} it does not drop while their entities are
     * unloaded or not yet re-registered after a load (2026-10-17: a reload let villager 2 past
     * the raw-food gate).
     */
    long knownVillagerCount() {
        return displayNumbers.byVuid().size();
    }

    int getHighestDisplayNumber() {
        return displayNumbers.highest();
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
        moods.tryApplyEffect(effect, expireOnTick, uuid);
    }

    ImmutableMap<UUID, Integer> getFullness() {
        return ImmutableMap.copyOf(delegate.fullness);
    }

    ImmutableMap<UUID, ImmutableList<Effect>> getMoodEffects() {
        return ImmutableMap.copyOf(moods.getEffects());
    }

    /** True when the townie currently has an assigned bed the town's rooms provide. */
    public boolean hasBed(VisitorMobEntity v) {
        return sleep.hasBed(v);
    }

    /** True when the given mood effect is currently active on the townie. */
    public boolean hasMoodEffect(UUID uuid, ResourceLocation effect) {
        return moods.hasEffect(uuid, effect);
    }

    float getProficiency(
            UUID uuid,
            String proficiencyId
    ) {
        return proficiencies.getLevel(uuid, proficiencyId);
    }

    ImmutableMap<String, Float> getProficiencies(UUID uuid) {
        return proficiencies.getAll(uuid);
    }

    ImmutableMap<UUID, ImmutableMap<String, Float>> getAllProficiencies() {
        return proficiencies.getAll();
    }

    boolean isProficiencySeeded(UUID uuid) {
        return proficiencies.isSeeded(uuid);
    }

    void setProficiency(
            UUID uuid,
            String proficiencyId,
            float level
    ) {
        proficiencies.setLevel(uuid, proficiencyId, level);
    }

    void setProficiencies(
            UUID uuid,
            Map<String, Float> levels
    ) {
        proficiencies.setAll(uuid, levels);
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

    int getLevel(UUID villagerUUID) {
        return delegate.getLevel(villagerUUID);
    }

    ImmutableMap<UUID, Map<JobID, ? extends Collection<JobID>>> getJobsKnownToExist() {
        return ImmutableMap.copyOf(learning.jobsKnownToExist);
    }

    /**
     * The set of jobs the town KNOWS (knowledge-tree parents + children,
     * unioned across villagers, plus unlocked jobs) - see
     * {@link TownVillagerLearningHandle#getTownKnownJobs}. Public for the
     * work-request confirm hint (core.network).
     */
    public ImmutableSet<JobID> getTownKnownJobs() {
        return learning.getTownKnownJobs();
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

    /** Fallback readiness: rested old enough; used when there is no work and no fallback (idle-rest). */
    boolean isReadyForDowntimeIfIdle(
            VillagerUUID ownerVUID,
            long tick
    ) {
        return delegate.isReadyForDowntimeIfIdle(getUuid(ownerVUID), tick);
    }

    void markWorked(VillagerUUID ownerVUID, long tick) {
        delegate.markWorked(getUuid(ownerVUID), tick);
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

    ImmutableMap<UUID, JobID> getGuaranteedRoots() {
        return delegate.getGuaranteedRoots();
    }

    ImmutableMap<UUID, JobID> getPreRolls() {
        return delegate.getPreRolls();
    }

    void register(VisitorMobEntity visitorMobEntity) {
        delegate.register(visitorMobEntity);

        assignDisplayNumber(visitorMobEntity.getUUID());
        int displayNumber = displayNumberFor(visitorMobEntity.getUUID());
        if (displayNumber > 0) {
            visitorMobEntity.setCustomName(VillagerDisplayNumbers.nameplate(displayNumber));
            visitorMobEntity.setCustomNameVisible(true);
        }

        seedProficiencies(visitorMobEntity.getUUID());

        ImmutableList<JobID> defaultWork = ServerJobsRegistry.getDefaultWork(visitorMobEntity.getJobId());
        for (JobID jobID : defaultWork) {
            learning.unlockJob(visitorMobEntity.getUUID(), jobID);
        }
        learning.requestKnowledge(visitorMobEntity.getUUID(), defaultWork);
    }

    /**
     * Seed a freshly-spawned townie with random proficiencies, unless they already carry a
     * persisted map (loaded/relocated townies keep theirs — the wake path overwrites this seed
     * with their saved levels). See ADR-0010.
     */
    private void seedProficiencies(UUID uuid) {
        if (proficiencies.isSeeded(uuid)) {
            return;
        }
        Map<String, Float> seed = ca.bradj.questown.jobs.Proficiency.seed(
                uuid,
                ServerJobsRegistry.getAllDeclaredProficiencyIds(),
                Config.PROFICIENCY_SEED_COUNT.get()
        );
        proficiencies.setAll(uuid, seed);
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

    public boolean isStarving(@Nullable VillagerUUID vuid) {
        return delegate.isStarving(getUuid(vuid));
    }

    public boolean gaveUpDiningRecently(@Nullable VillagerUUID vuid) {
        return delegate.gaveUpDiningRecently(getUuid(vuid), getTick());
    }
}
