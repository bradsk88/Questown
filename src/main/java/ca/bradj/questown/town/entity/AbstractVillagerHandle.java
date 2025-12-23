package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.items.EffectMetaItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class SimpleVillagerHandle<DATA, ENTITY> {

    private final Delegator<ENTITY> delegator;

    public interface Delegator<ENTITY> {
        JobID getJobId(ENTITY vEntity);

        UUID getUUID(ENTITY v);

        void setJob(
                UUID visitorUUID,
                JobID jobName,
                ENTITY f
        );

        void setChanged();

        void addSleepListener(BiConsumer<TownPosition, Long> listener);

        void claimBed(ENTITY vEntity);

        Double getHealFactor(TownPosition bedPos);

        void debug(
                QT.QTLogger villagerLogger,
                String villagerStats,
                String s,
                Object... args
        );

        ENTITY checkType();
    }

    // Unserialized
    private final Map<UUID, DATA> customData = new HashMap<>();
    private final Map<UUID, Long> mostRecentDowntimeTick = new HashMap<>();
    private final Map<UUID, Long> mostRecentFoodAttempt = new HashMap<>();
    private final Map<UUID, Boolean> starving = new HashMap<>();

    // Serialized
    private final Map<UUID, Boolean> jobChangesPending = new HashMap<>();

    public static void staticInit() {
        TownVillagerUIs.staticInit();
    }

    final Map<UUID, Integer> fullness = new HashMap<>();
    final Map<UUID, Integer> experience = new HashMap<>();
    final Map<UUID, Integer> levels = new HashMap<>();
    final Map<UUID, Integer> damage = new HashMap<>();
    final Map<UUID, PoseInPlace> requestedPose = new HashMap<>();
    final Map<UUID, Boolean> hasBlockOfProgress = new HashMap<>();

    public ImmutableMap<UUID, Boolean> getJobChangesPending() {
        return ImmutableMap.copyOf(jobChangesPending);
    }


    final TownVillagerMoods moods = new TownVillagerMoods();

    private final List<ENTITY> entities = new ArrayList<>();
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private final List<Consumer<VisitorMobEntity>> hungryListeners = new ArrayList<>();

    private static final int TICK_FACTOR = 10;
    private final TownVillagerBedsHandle beds = new TownVillagerBedsHandle();
    final TownVillagerLearningHandle learning = new TownVillagerLearningHandle();

    public SimpleVillagerHandle(
            Delegator del
    ) {
        this.delegator = del;
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, ? extends ImmutableCollection<Effect>> moodEffects,
            Map<UUID, Integer> damage,
            Map<UUID, ? extends ImmutableCollection<JobID>> unlockedJobs,
            Map<UUID, ? extends Map<JobID, ? extends ImmutableCollection<JobID>>> jobsKnownToExist,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<VillagerUUID, Boolean> jobChangesPending
    ) {
        if (!this.fullness.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already initialized");
        }
        this.fullness.putAll(fullness);
        this.moods.initialize(moodEffects);
        this.damage.putAll(damage);
        this.learning.initialize(unlockedJobs, jobsKnownToExist);
        this.experience.putAll(experience);
        this.levels.putAll(level);
        this.jobChangesPending.putAll(jobChangesPending);
        addHungryListener(e -> {
            if (UtilClean.getOrDefault(fullness, e.getUUID(), Config.BASE_FULLNESS.get()) == 0) {
                starving.put(e.getVUID(), true);
            }
        });
    }

    public void tick(
            long currentTick,
            Signals signals
    ) {
        if (signals != Signals.NIGHT) {
            tickHunger();
        }
        tickDamage();
        moods.tick(currentTick);
        TownFlagBlockEntity t = town.getUnsafe();
        beds.tick(t, ImmutableList.copyOf(entities));
        learning.tick(ImmutableList.copyOf(entities), currentTick);
        entities.forEach(e -> {
            Optional<GlobalPos> bestBed = beds.getBestBed(t, e);
            delegator.setHomeMemory(bestBed);
        });
    }

    private void tickHunger() {
        if (!Config.HUNGER_ENABLED.get()) {
            entities.forEach(e -> {
                logHunger("Hunger filled for {}", UtilClean.truncateMiddle(e.getUUID()));
                fullness.put(e.getUUID(), Config.BASE_FULLNESS.get());
            });
            return;
        }

        Map<UUID, Integer> map = fullness;
        Integer base = Config.BASE_FULLNESS.get();
        BiConsumer<Integer, LivingEntity> then = (newVal, e) -> {
            logHunger("Updating hunger level to {} for {}", newVal, UtilClean.truncateMiddle(e.getUUID()));
            if (Math.abs(newVal) % 10 == 0) {
                logHunger("Broadcasting starving status for {}", UtilClean.truncateMiddle(e.getUUID()));
                hungryListeners.forEach(l -> l.accept((VisitorMobEntity) e));
            }
        };
        tickThing(map, base, e -> Math.toIntExact(Config.FLAG_TICK_INTERVAL.get()), -Integer.MAX_VALUE, then);
    }
    private void logHunger(
            String s,
            Object... args
    ) {
        town.getUnsafe().getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.HUNGER_UPDATES).log(s, args);
    }

    private void tickDamage() {
        tickThing(
                damage, 0, e -> applyHealFactor(e, 100), 0, (newVal, e) -> {
                }
        );
    }

    private int applyHealFactor(
            LivingEntity e,
            int i
    ) {
        if (!(e instanceof VisitorMobEntity)) {
            return i;
        }
        PoseInPlace pose = requestedPose.get(e.getUUID());
        if (pose == null) {
            return i;
        }
        if (!Pose.SLEEPING.equals(pose.pose())) {
            return i;
        }
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        Double bedFactor = Config.NORMAL_BED_HEAL_MULTIPLIER.get();
        Double boostedFactor = t.getHealingHandle().getHealFactor(e.blockPosition());
        Double hf = Math.min(bedFactor, boostedFactor);
        int i1 = (int) (i * hf);
        t.getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_STATS).log(
                "Healing by {} due to sleeping heal factor {} {}", i1, hf, e.getUUID()
        );
        return i1;
    }

    private void tickThing(
            Map<UUID, Integer> map,
            Integer base,
            Function<LivingEntity, Integer> amount,
            int min,
            BiConsumer<Integer, LivingEntity> then
    ) {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            int oldVal = map.getOrDefault(u, base);
            int newVal = Math.max(min, oldVal - amount.apply(e));
            map.put(u, newVal);
            if (newVal != oldVal) {
                listeners.forEach(l -> l.accept(getStats(u)));
            }
            then.accept(newVal, e);
        });
    }

    public VillagerStatsData getStats(UUID uuid) {
        Integer bf = Config.BASE_FULLNESS.get();
        float fullnessPercent = (float) Util.getOrDefault(fullness, uuid, bf) / bf;
        float damagePercent = getDamagePercent(uuid);
        int experienceNum = Util.getOrDefault(experience, uuid, 0);
        int experienceTarget = (int) getExpForCurrentLevel(uuid);
        return new VillagerStatsData(
                // TODO[Traits]: Track max unique fullness level per villager
                fullnessPercent, experienceNum, experienceTarget, moods.getMood(uuid), damagePercent);
    }

    private float getExpForCurrentLevel(UUID uuid) {
        Integer level = UtilClean.getOrDefault(levels, uuid, 1);
        Integer baseExp = Config.EXPERIENCE_REQUIRED_AT_LEVEL_1.get();
        Double rampFactor = Config.EXPERIENCE_RAMP_FACTOR.get();
        return (float) (baseExp * Math.pow(rampFactor, level - 1));
    }

    public float getDamagePercent(UUID uuid) {
        return (float) Util.getOrDefault(damage, uuid, 0) / (16 * Config.DAMAGE_TICKS.get() * TICK_FACTOR);
    }

    public Collection<JobID> getJobs() {
        return entities.stream().map(v -> ((VisitorMobEntity) v).getJobId()).toList();
    }

    public ImmutableMap<UUID, JobID> getVillagerJobs() {
        Map<UUID, JobID> b = new HashMap<>();
        entities.stream()
                .filter(v -> delegator.getUUID(v) != null) // TODO: Why would a villager have a null UUID?
                .forEach(v -> b.put(delegator.getUUID(v), delegator.getJobId(v)));
        return ImmutableMap.copyOf(b);
    }

    public void changeJobForVillager(
            UUID villagerUUID,
            JobID newJob,
            Long currentTick,
            boolean announce
    ) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        ENTITY f = getEntity(villagerUUID);
        if (f == null) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", villagerUUID, newJob);
            return;
        }
        JobID oldJob = delegator.getJobId(f);
        if (DowntimeWork.matches(delegator.getJobId(f))) {
            registerMostRecentDowntime(villagerUUID, currentTick);
        }
        if (DinerRawFoodWork.isDining(oldJob)) {
            registerMostRecentFoodAttempt(villagerUUID, currentTick);
        }

        doSetJob(villagerUUID, newJob, f);
        t.setChanged();
        if (announce) {
            t.messages.jobChanged(newJob, villagerUUID);
        }

        t.possibleWork.invalidate();
        if (!newJob.sameRoot(oldJob)) {
            jobChangesPending.put(delegator.getUUID(f), false);
        }
    }

    private void doSetJob(
            UUID visitorUUID,
            JobID jobName,
            ENTITY f
    ) {
        delegator.setJob(visitorUUID, jobName, f);
    }

    public Stream<ENTITY> stream() {
        return entities.stream();
    }

    public void remove(ENTITY visitorMobEntity) {
        this.entities.remove(visitorMobEntity);
        delegator.setChanged();
    }

    public void fillHunger(UUID uuid) {
        fillHunger(uuid, 1.0f);
    }

    public void fillHunger(
            UUID uuid,
            float percent
    ) {
        // TODO: Get max fullness from villager
        int newHunger = (int) (percent * Config.BASE_FULLNESS.get());
        QT.VILLAGER_LOGGER.info("Fullness changed from {} to {}", fullness.get(uuid), newHunger);
        fullness.put(uuid, newHunger);
        if (percent > 0) {
            starving.put(uuid, false);
        }
    }

    void forEach(Consumer<ENTITY> c) {
        List<ENTITY> villagers = this.entities.stream()
                                                        .filter(v -> v instanceof ENTITY)
                                                        .map(v -> (ENTITY) v)
                                                        .toList();
        for (ENTITY villager : villagers) {
            c.accept(villager);
        }
    }

    public boolean isEmpty() {
        return this.entities.isEmpty();
    }

    public long size() {
        return entities.size();
    }

    public void add(ENTITY vEntity) {
        this.entities.add(vEntity);

        ImmutableList<JobID> defaultWork = ServerJobsRegistry.getDefaultWork(delegator.getJobId(vEntity));
        for (JobID jobID : defaultWork) {
            unlockJob(delegator.getUUID(vEntity), jobID);
        }

        learning.requestKnowledge(delegator.getUUID(vEntity), defaultWork);

        delegator.claimBed(vEntity);
        delegator.addSleepListener((TownPosition bedPos, Long duration) -> {
            Double healFactor = delegator.getHealFactor(bedPos);
            long ticksHealed = (long) (duration * healFactor);
            damage.compute(
                    delegator.getUUID(vEntity), (id, cur) -> {
                        if (cur == null) {
                            return 0;
                        }
                        int newVal = Math.toIntExact(Math.max(0, cur - ticksHealed));
                        delegator.debug(
                                QT.VILLAGER_LOGGER,
                                DebugLogArgument.VILLAGER_STATS,
                                "Villager damage changed from {} to {} after {} ticks of sleep via bed at {} with heal factor {} [{}]",
                                cur,
                                newVal,
                                duration,
                                bedPos,
                                healFactor,
                                delegator.getUUID(vEntity)
                        );
                        return newVal;
                    }
            );
        });
    }

    public boolean exists(VisitorMobEntity visitorMobEntity) {
        return entities.contains(visitorMobEntity);
    }

    public void addStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.add(l);
    }

    public void addHungryListener(Consumer<VisitorMobEntity> l) {
        this.hungryListeners.add(l);
    }

    public void removeStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.remove(l);
    }

    public Collection<ENTITY> entities() {
        return this.entities;
    }

    public void makeAllTotallyHungry() {
        if (!Config.HUNGER_ENABLED.get()) {
            return;
        }
        entities.forEach(e -> {
            UUID u = delegator.getUUID(e);
            fullness.put(u, 1);
            starving.put(u, true);
            // Listeners will be notified on next tick
        });
    }

    public boolean isDining(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(delegator.getUUID(v)))
                       .map(v -> ServerJobsRegistry.isDining(((VisitorMobEntity) v).getJobId())).findFirst()
                       .orElse(false);
    }

    public boolean canDine(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(delegator.getUUID(v)))
                       .map(v -> ((VisitorMobEntity) v).canStopWorkingAtAnyTime()).findFirst().orElse(false);
    }

    public void applyEffect(
            ResourceLocation effect,
            Long expireOnTick,
            UUID uuid
    ) {
        // TODO: Generalize
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER.equals(effect)) {
            fillHunger(uuid);
            return;
        }
        if (EffectMetaItem.ConsumableEffects.FILL_HUNGER_HALF.equals(effect)) {
            fillHunger(uuid, 0.5f);
            return;
        }
        moods.tryApplyEffect(effect, expireOnTick, uuid);
    }

    public int getAffectedTime(
            UUID uuid,
            Integer timeToAugment
    ) {
        float offset = ((Config.NEUTRAL_MOOD.get() / 100f) - moods.getMood(uuid));
        return (int) ((1f + offset) * timeToAugment);
    }

    public int getWorkSpeed(UUID uuid) {
        return (int) (moods.getMood(uuid) * 10);
    }

    public void associate(TownFlagBlockEntity t) {
        this.learning.associate(t);
    }

    public void freezeVillagers(Integer ticks) {
        stream().filter(VisitorMobEntity.class::isInstance).map(VisitorMobEntity.class::cast)
                .forEach(v -> v.freeze(ticks));
    }

    public void validateEntity(VisitorMobEntity visitorMobEntity) {
        if (exists(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    public ENTITY getEntity(VillagerUUID ownerUUID) {
        Optional<ENTITY> f = stream().filter(v -> ownerUUID.matches(delegator.getUUID(v))).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No entities found for UUID: {}", ownerUUID);
            return null;
        }
        return f.get();
    }

    public void addDamage(UUID uuid) {
        Integer oldVal = Util.getOrDefault(damage, uuid, 0);
        int addition = (int) (Config.DAMAGE_TICKS.get() * TICK_FACTOR);
        damage.put(uuid, oldVal + addition);
    }

    public int getDamageTicksLeft(UUID uuid) {
        return Util.getOrDefault(damage, uuid, 0) / TICK_FACTOR;
    }

    public void requestPose(
            UUID ownerUUID,
            PoseInPlace pose
    ) {
        requestedPose.put(ownerUUID, pose);
    }

    public Optional<PoseInPlace> getRequestedPose(UUID ownerUUID) {
        return Optional.ofNullable(requestedPose.get(ownerUUID));
    }

    public void clearPoseRequests(UUID uuid) {
        requestedPose.remove(uuid);
    }

    public void setJobChangePending(
            @Nullable VillagerUUID vuid,
            boolean value
    ) {
        jobChangesPending.put(vuid, value);
        town.getUnsafe().setChanged();
    }

    public boolean isJobChangePending(ENTITY vuid) {
        return UtilClean.getOrDefault(jobChangesPending, vuid, false);
    }

    public boolean isStarving(@Nullable VillagerUUID vuid) {
        return UtilClean.getOrDefault(starving, vuid, false);
    }

    public void setStarving(
            @Nullable VillagerUUID vuid,
            boolean b
    ) {
        starving.put(vuid, true);
    }

    public boolean gaveUpRecently(
            VillagerUUID uuid,
            long currentTick
    ) {
        long giveUpTick = UtilClean.getOrDefault(mostRecentFoodAttempt, uuid, -1L);
        long ticksSince = currentTick - giveUpTick;
        if (ticksSince > Compat.configGet(Config.BUFFER_TICKS_AFTER_FOOD_ATTEMPT).get()) {
            mostRecentFoodAttempt.remove(uuid);
            return false;
        }
        return true;
    }

    public void register(VisitorMobEntity vEntity) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        QT.FLAG_LOGGER.info("Registered entity with town {}: {}", t.getUUID(), vEntity);
        this.add(vEntity);
        vEntity.addChangeListener(() -> {
            TownInterface.DebugLogger logger = t.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.TOWN_STATE_CHANGES);
            logger.log("Entity requests flag to be marked changed");
            t.setChanged();
        });
        t.setChanged();
    }

    public void unlockJob(
            UUID villagerUUID,
            JobID id
    ) {
        learning.unlockJob(villagerUUID, id);
    }

    public void addExperience(
            UUID uuid,
            int exp
    ) {
        if (hasBlockOfProgress(uuid)) {
            return;
        }
        int newExp = experience.compute(uuid, (x, cur) -> cur == null ? exp : cur + exp);
        int target = (int) getExpForCurrentLevel(uuid);
        if (newExp >= target) {
            Integer newLvl = levels.compute(uuid, (x, cur) -> cur == null ? 2 : cur + 1);
            experience.put(uuid, newExp % target);
            town.getUnsafe().messages.broadcastMessage(
                    "message.villager.leveled_up",
                    UtilClean.truncateMiddle(uuid),
                    newLvl
            );
            hasBlockOfProgress.put(uuid, true);
        }
    }

    public boolean hasBlockOfProgress(UUID uuid) {
        return Util.getOrDefault(hasBlockOfProgress, uuid, false);
    }

    public void clearBlockOfProgress(UUID uuid) {
        hasBlockOfProgress.put(uuid, false);
    }

    public void scheduleJobRootChange(
            UUID villagerUUID,
            boolean instant
    ) {
        VisitorMobEntity e = getEntity(villagerUUID);
        if (e == null) {
            QT.FLAG_LOGGER.error("Villager not found for job root change: {}", villagerUUID);
            return;
        }
        if (instant) {
            QT.FLAG_LOGGER.info(
                    "Villager {} will change to a new job NOW (creative mode)",
                    UtilClean.truncateMiddle(villagerUUID)
            );
            changeJobRootNow(e);
            return;
        }

        jobChangesPending.put(e.getVUID(), true);
        TownFlagBlockEntity t = town.getUnsafe();
        t.messages.broadcastMessage(
                "message.questown.villager.change_job_in_morning",
                UtilClean.truncateMiddle(villagerUUID)
        );
    }

    public boolean isUnlocked(JobID jobID) {
        return learning.isUnlocked(jobID);
    }

    public UnsafeVillagerData getUnprotectedDataHandle(@Nullable VillagerUUID vuid) {
        return new UnsafeVillagerData() {
            public String get(String key) {

                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                if (!tag.contains(key)) {
                    return null;
                }
                return tag.getString(key);
            }

            public void write(
                    String key,
                    String value
            ) {

                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                tag.putString(key, value);
                customData.put(vuid, tag);
            }

            public void clear(String key) {
                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                tag.remove(key);
                customData.put(vuid, tag);
            }
        };
    }

    public Optional<Entity> getLookTarget(@Nullable VillagerUUID vuid) {
        LookTarget tar = lookTargets.get(vuid);
        if (tar == null) {
            return Optional.empty();
        }
        long currentTick = Util.getTick(town.getServerLevelUnsafe());
        if (currentTick < tar.untilTick()) {
            return Optional.of(tar.who);
        }
        town.getUnsafe().getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                "Look target for {} has expired at tick {} (current {})",
                vuid,
                tar.untilTick(),
                currentTick
        );
        lookTargets.remove(vuid);
        return Optional.empty();
    }

    public void setLookTarget(
            @Nullable VillagerUUID vuid,
            Entity entity,
            long untilTick,
            long thenNotUntilTick
    ) {
        LookTarget tar = mostRecentLookTarget.get(vuid);
        if (tar != null && tar.who.equals(entity)) {
            long currentTick = Util.getTick(town.getServerLevelUnsafe());
            if (currentTick < tar.untilTick()) {
                return;
            }
            mostRecentLookTarget.remove(vuid);
        }
        lookTargets.computeIfAbsent(
                vuid, (k) -> {
                    mostRecentLookTarget.put(k, new LookTarget(entity, thenNotUntilTick));
                    LookTarget lookTarget = new LookTarget(entity, untilTick);
                    town.getUnsafe().getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_NAVIGATION).log(
                            "Setting look target for {} to {} until tick {} (then not until {})",
                            vuid,
                            entity,
                            untilTick,
                            thenNotUntilTick
                    );
                    return lookTarget;
                }
        );
    }

    public ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs() {
        return learning.getUnlockedJobs();
    }

    public ImmutableMap<UUID, ImmutableMap<JobID, ImmutableSet<JobID>>> getChildJobsKnownToExist() {
        return learning.getChildJobsKnownToExist();
    }

    public void handleMorning() {
        forEach(LivingEntity::stopSleeping);
        makeAllTotallyHungry();
        long tick = Util.getTick(town.getServerLevelUnsafe());
        forEach(v -> registerMostRecentDowntime(v, tick));
        forEach(v -> {
            if (!isJobChangePending(v)) {
                return;
            }
            changeJobRootNow(v);
        });
    }

    private void changeJobRootNow(ENTITY v) {
        Optional<JobID> override = town.getUnsafe().getQuestHandle().overnightJobOverride();
        if (override.isPresent()) {
            QT.FLAG_LOGGER.info("Overriding random job root change in favor of: {}", override.get());
            unlockAndChange(v, override.get());
            return;
        }

        ImmutableSet<JobID> allRoots = ServerJobsRegistry.getAllRootJobs();
        List<JobID> allOtherJobs = allRoots.stream().filter(z -> !delegator.getJobId(v).sameRoot(z)).toList();
        QT.FLAG_LOGGER.info(
                "Changing villager from {} to one of [{}]",
                delegator.getJobId(v).rootId(),
                String.join(", ", allRoots.stream().map(JobID::rootId).toList())
        );
        if (allOtherJobs.isEmpty()) {
            QT.FLAG_LOGGER.error("Only one job root detected in town? This is likely a poorly configured data pack.");
            jobChangesPending.put(delegator.getUUID(v), false);
            return;
        }
        ImmutableList<JobID> shuffled = Compat.shuffle(
                ImmutableSet.copyOf(allOtherJobs),
                town.getServerLevelUnsafe()
        );
        JobID newJob = shuffled.get(0);
        if (delegator.getJobId(v).sameRoot(newJob)) {
            QT.logBug("Root change resulted in same root. From {} to {}.", delegator.getJobId(v), newJob);
        }
        unlockAndChange(v, newJob);
    }

    private void unlockAndChange(
            ENTITY v,
            JobID newJob
    ) {
        QT.FLAG_LOGGER.info("Changing villager from {} to {}", delegator.getJobId(v).rootId(), newJob);
        unlockJob(delegator.getUUID(v), newJob);
        changeJobForVillager(delegator.getUUID(v), newJob, true);
    }

    public boolean isReadyForDowntime(
            UUID from,
            long currentTick
    ) {
        // TODO[Traits]: Consider augmenting downtime frequency for individual villagers
        Long maxTicksBeforeDowntime = Config.MAX_TICKS_BETWEEN_DOWNTIME.get();
        Long mostRecent = UtilClean.getOrDefault(mostRecentDowntimeTick, from, -maxTicksBeforeDowntime);
        return (currentTick - mostRecent) >= maxTicksBeforeDowntime;
    }

    public void registerMostRecentDowntime(
            UUID ownerUUID,
            long tick
    ) {
        mostRecentDowntimeTick.put(ownerUUID, tick);
    }

    private void registerMostRecentFoodAttempt(
            UUID ownerUUID,
            long tick
    ) {
        mostRecentFoodAttempt.put(ownerUUID, tick);
        QT.VILLAGER_LOGGER.info(
                "Villager will not prioritize food after giving up at tick {} [{}]",
                tick, UtilClean.truncateMiddle(ownerUUID)
        );
    }
}
