package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.PoseInPlace;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.VillagerStatsData;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class SimpleVillagerHandle<DATA, ENTITY> {

    private final Delegator<ENTITY> delegator;
    private boolean hungerEnabled = false;

    private Configs configs;

    public Map<UUID, ? extends Set<JobID>> getUnlockedJobs() {
        return delegator.getUnlockedJobs();
    }

    public Set<JobID> getChildJobsKnownToExist(JobID jobId) {
        return delegator.getChildJobsKnownToExist(jobId);
    }

    // Unserialized
    final Map<UUID, DATA> customData = new HashMap<>();
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

    private final List<ENTITY> entities = new ArrayList<>();
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private final List<Consumer<ENTITY>> hungryListeners = new ArrayList<>();

    private static final int TICK_FACTOR = 10;

    public record Configs(
            int baseFullness,
            boolean hungerEnabled,
            long flagTickInterval,
            double normalBedHealMultiplier,
            int expRequiredAtLevel1,
            double expRampFactor,
            long damageTicks,
            float neutralMood,
            long bufferTicksAfterFoodAttempt,
            long maxTicksBeforeDowntime
    ) {
    }

    public SimpleVillagerHandle(
            Delegator<ENTITY> del,
            Configs config
    ) {
        this.delegator = del;
        this.configs = config;
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, Integer> damage,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<UUID, Boolean> jobChangesPending,
            boolean hungerEnabled
    ) {
        if (!this.fullness.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already initialized");
        }
        this.fullness.putAll(fullness);
        this.damage.putAll(damage);
        this.experience.putAll(experience);
        this.levels.putAll(level);
        this.jobChangesPending.putAll(jobChangesPending);
        addHungryListener(e -> {
            if (UtilClean.getOrDefault(fullness, delegator.getUUID(e), configs.baseFullness) == 0) {
                starving.put(delegator.getUUID(e), true);
            }
        });
        this.hungerEnabled = hungerEnabled;
    }

    public void tick(
            Signals signals
    ) {
        if (signals != Signals.NIGHT) {
            tickHunger();
        }
        tickDamage();
        delegator.getSleepModule().tick(entities);
    }

    private void tickHunger() {
        if (!this.hungerEnabled) {
            entities.forEach(e -> {
                logHunger("Hunger filled for {}", UtilClean.truncateMiddle(delegator.getUUID(e)));
                fullness.put(delegator.getUUID(e), configs.baseFullness);
            });
            return;
        }

        Map<UUID, Integer> map = fullness;
        int base = configs.baseFullness;
        BiConsumer<Integer, ENTITY> then = (newVal, e) -> {
            logHunger("Updating hunger level to {} for {}", newVal, UtilClean.truncateMiddle(delegator.getUUID(e)));
            if (Math.abs(newVal) % 10 == 0) {
                logHunger("Broadcasting starving status for {}", UtilClean.truncateMiddle(delegator.getUUID(e)));
                hungryListeners.forEach(l -> l.accept(e));
            }
        };
        int min = -Integer.MAX_VALUE; // Hunger keeps ticking below zero to trigger dining
        tickThing(map, base, e -> Math.toIntExact(configs.flagTickInterval), min, then);
    }

    private void logHunger(
            String s,
            Object... args
    ) {
        delegator.debug(QT.VILLAGER_LOGGER, DebugLogArgument.HUNGER_UPDATES, s, args);
    }

    private void tickDamage() {
        tickThing(
                damage, 0, e -> applyHealFactor(e, 100), 0, (newVal, e) -> {
                }
        );
    }

    private int applyHealFactor(
            ENTITY e,
            int input
    ) {
        if (!delegator.getSleepModule().isSleeping(e)) {
            return input;
        }
        Double bedFactor = configs.normalBedHealMultiplier;
        Double boostedFactor = delegator.getHealingModule().getHealFactor(e);
        double hf = Math.min(bedFactor, boostedFactor);
        int i1 = (int) (input * hf);
        delegator.debug(
                QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_STATS,
                "Healing by {} due to sleeping heal factor {} {}", i1, hf, delegator.getUUID(e)
        );
        return i1;
    }

    private void tickThing(
            Map<UUID, Integer> map,
            Integer base,
            Function<ENTITY, Integer> amount,
            int min,
            BiConsumer<Integer, ENTITY> then
    ) {
        entities.forEach(e -> {
            UUID u = delegator.getUUID(e);
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
        Integer bf = configs.baseFullness;
        float fullnessPercent = (float) Util.getOrDefault(fullness, uuid, bf) / bf;
        float damagePercent = getDamagePercent(uuid);
        int experienceNum = Util.getOrDefault(experience, uuid, 0);
        int experienceTarget = (int) getExpForCurrentLevel(uuid);
        return new VillagerStatsData(
                // TODO[Traits]: Track max unique fullness level per villager
                fullnessPercent, experienceNum, experienceTarget, delegator.getMood(uuid), damagePercent);
    }

    private float getExpForCurrentLevel(UUID uuid) {
        Integer level = UtilClean.getOrDefault(levels, uuid, 1);
        Integer baseExp = configs.expRequiredAtLevel1;
        Double rampFactor = configs.expRampFactor;
        return (float) (baseExp * Math.pow(rampFactor, level - 1));
    }

    public float getDamagePercent(UUID uuid) {
        return (float) Util.getOrDefault(damage, uuid, 0) / (16 * configs.damageTicks * TICK_FACTOR);
    }

    public Collection<JobID> getJobs() {
        return entities.stream().map(delegator::getJobId).toList();
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

        doSetJob(villagerUUID, newJob, f, announce);

        delegator.invalidateCachedWorkPossibilities();
        if (!newJob.sameRoot(oldJob)) {
            jobChangesPending.put(delegator.getUUID(f), false);
        }
    }

    private void doSetJob(
            UUID visitorUUID,
            JobID jobName,
            ENTITY f,
            boolean announce
    ) {
        delegator.setJob(visitorUUID, jobName, f, announce);
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
        int newHunger = (int) (percent * configs.baseFullness);
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

        delegator.getSleepModule().claimBed(vEntity);
        delegator.getSleepModule().addSleepListener(
                vEntity, (TownPosition bedPos, Long duration) -> {
                    Double healFactor = delegator.getHealingModule().getHealFactor(vEntity);
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
                }
        );
    }

    public boolean exists(ENTITY visitorMobEntity) {
        return entities.contains(visitorMobEntity);
    }

    public void addStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.add(l);
    }

    public void addHungryListener(Consumer<ENTITY> l) {
        this.hungryListeners.add(l);
    }

    public void removeStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.remove(l);
    }

    public Collection<ENTITY> entities() {
        return this.entities;
    }

    public void makeAllTotallyHungry() {
        if (!configs.hungerEnabled) {
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
                       .map(delegator::isDining).findFirst()
                       .orElse(false);
    }

    public boolean canDine(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(delegator.getUUID(v)))
                       .map(delegator::canStopWorkingAtAnyTime)
                       .findFirst()
                       .orElse(false);
    }

    public int getAffectedTime(
            UUID uuid,
            Integer timeToAugment
    ) {
        float offset = ((configs.neutralMood / 100f) - delegator.getMood(uuid));
        return (int) ((1f + offset) * timeToAugment);
    }

    public int getWorkSpeed(UUID uuid) {
        return (int) (delegator.getMood(uuid) * 10);
    }

    public void associate(TownFlagBlockEntity t) {
    }

    public void freezeVillagers(Integer ticks) {
        stream().forEach(v -> delegator.freeze(v, ticks));
    }

    public void validateEntity(ENTITY visitorMobEntity) {
        if (exists(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        delegator.discardEntity(visitorMobEntity);
    }

    public ENTITY getEntity(UUID ownerUUID) {
        Optional<ENTITY> f = stream().filter(v -> delegator.isUUID(v, ownerUUID)).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No entities found for UUID: {}", ownerUUID);
            return null;
        }
        return f.get();
    }

    public void addDamage(UUID uuid) {
        Integer oldVal = Util.getOrDefault(damage, uuid, 0);
        int addition = (int) (configs.damageTicks * TICK_FACTOR);
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
            @Nullable UUID vuid,
            boolean value
    ) {
        jobChangesPending.put(vuid, value);
        delegator.setChanged();
    }

    public boolean isJobChangePending(UUID vuid) {
        return UtilClean.getOrDefault(jobChangesPending, vuid, false);
    }

    public boolean isStarving(@Nullable UUID vuid) {
        return UtilClean.getOrDefault(starving, vuid, false);
    }

    public void setStarving(
            @Nullable UUID vuid,
            boolean b
    ) {
        starving.put(vuid, true);
    }

    public boolean gaveUpDiningRecently(
            UUID uuid,
            long currentTick
    ) {
        long giveUpTick = UtilClean.getOrDefault(mostRecentFoodAttempt, uuid, -1L);
        long ticksSince = currentTick - giveUpTick;
        if (ticksSince > configs.bufferTicksAfterFoodAttempt) {
            mostRecentFoodAttempt.remove(uuid);
            return false;
        }
        return true;
    }

    public void register(ENTITY vEntity) {
        QT.FLAG_LOGGER.info("Registered entity with town {}: {}", delegator.getUUID(vEntity), vEntity);
        this.add(vEntity);
        delegator.addChangeListener(
                vEntity, () -> {
                    delegator.debug(
                            QT.FLAG_LOGGER,
                            DebugLogArgument.TOWN_STATE_CHANGES,
                            "Entity requests flag to be marked changed"
                    );
                    delegator.setChanged();
                }
        );
        delegator.setChanged();
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
            delegator.broadcastMessage(
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
            long currentTick,
            boolean instant
    ) {
        ENTITY e = getEntity(villagerUUID);
        if (e == null) {
            QT.FLAG_LOGGER.error("Villager not found for job root change: {}", villagerUUID);
            return;
        }
        if (instant) {
            QT.FLAG_LOGGER.info(
                    "Villager {} will change to a new job NOW (creative mode)",
                    UtilClean.truncateMiddle(villagerUUID)
            );
            changeJobRootNow(e, currentTick);
            return;
        }

        jobChangesPending.put(delegator.getUUID(e), true);
        delegator.broadcastMessage(
                "message.questown.villager.change_job_in_morning",
                UtilClean.truncateMiddle(villagerUUID)
        );
    }

    public void handleMorning(long tick) {
        forEach(delegator.getSleepModule()::stopSleeping);
        makeAllTotallyHungry();
//        long tick = Util.getTick(town.getServerLevelUnsafe());
        forEach(v -> registerMostRecentDowntime(delegator.getUUID(v), tick));
        forEach(v -> {
            if (!isJobChangePending(delegator.getUUID(v))) {
                return;
            }
            changeJobRootNow(v, tick);
        });
    }

    private void changeJobRootNow(
            ENTITY v,
            long currentTick
    ) {
//        Optional<JobID> override = town.getUnsafe().getQuestHandle().overnightJobOverride();
        Optional<JobID> override = delegator.getOvernightJobOverride();
        if (override.isPresent()) {
            QT.FLAG_LOGGER.info("Overriding random job root change in favor of: {}", override.get());
            unlockAndChange(v, override.get(), currentTick);
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
        ImmutableList<JobID> shuffled = delegator.shuffle(
                ImmutableSet.copyOf(allOtherJobs)
        );
        JobID newJob = shuffled.get(0);
        if (delegator.getJobId(v).sameRoot(newJob)) {
            QT.logBug("Root change resulted in same root. From {} to {}.", delegator.getJobId(v), newJob);
        }
        unlockAndChange(v, newJob, currentTick);
    }

    private void unlockAndChange(
            ENTITY v,
            JobID newJob,
            long currentTick
    ) {
        QT.FLAG_LOGGER.info("Changing villager from {} to {}", delegator.getJobId(v).rootId(), newJob);
        delegator.unlockJob(v, newJob);
        changeJobForVillager(delegator.getUUID(v), newJob, currentTick, true);
    }

    public boolean isReadyForDowntime(
            UUID from,
            long currentTick
    ) {
        // TODO[Traits]: Consider augmenting downtime frequency for individual villagers
        Long maxTicksBeforeDowntime = configs.maxTicksBeforeDowntime;
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
