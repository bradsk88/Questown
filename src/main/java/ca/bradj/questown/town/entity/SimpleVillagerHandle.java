package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.RootRoll;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.MoodEffects;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SimpleVillagerHandle<DATA, ENTITY> {

    private final Delegator<ENTITY> delegator;
    private boolean hungerEnabled = false;

    private Configs configs;

    /** Which villagers had no bed last night; at dawn each gets the sleepless effect. */
    private final BedlessLedger bedlessLedger = new BedlessLedger();

    public Map<UUID, ? extends Set<JobID>> getUnlockedJobs() {
        return delegator.getUnlockedJobs();
    }

    public Set<JobID> getChildJobsKnownToExist(JobID jobId) {
        return delegator.getChildJobsKnownToExist(jobId);
    }

    // Unserialized
    final Map<UUID, DATA> customData = new HashMap<>();
    private final Map<UUID, DowntimeState> downtime = new HashMap<>();
    private final Map<UUID, Long> mostRecentFoodAttempt = new HashMap<>();
    private final Map<UUID, Boolean> starving = new HashMap<>();

    // Serialized
    private final Map<UUID, Boolean> jobChangesPending = new HashMap<>();
    // The job an open job-change quest promised at the spend, until the change applies.
    private final Map<UUID, JobID> guaranteedRoots = new HashMap<>();
    // The job rolled when a Block of Progress was spent, for changes no quest promised; applied in the morning. Saved.
    private final Map<UUID, JobID> preRolls = new HashMap<>();

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

    public ImmutableMap<UUID, JobID> getGuaranteedRoots() {
        return ImmutableMap.copyOf(guaranteedRoots);
    }

    public ImmutableMap<UUID, JobID> getPreRolls() {
        return ImmutableMap.copyOf(preRolls);
    }

    private final List<ENTITY> entities = new ArrayList<>();
    // By villager: a stats page hears only its own villager.
    private final Map<UUID, List<Consumer<VillagerStatsData>>> listeners = new HashMap<>();
    // Any villager's stats changed, so the flag saves.
    private final List<Runnable> anyStatsListeners = new ArrayList<>();
    private final List<Consumer<ENTITY>> hungryListeners = new ArrayList<>();

    private static final int TICK_FACTOR = 10;

    public void toggleHunger() {
        configs = new Configs(
                configs.baseFullness,
                !configs.hungerEnabled,
                configs.flagTickInterval,
                configs.normalBedHealMultiplier,
                configs.expRequiredAtLevel1,
                configs.expRampFactor,
                configs.damageTicks,
                configs.neutralMood,
                configs.bufferTicksAfterFoodAttempt,
                configs.maxTicksBeforeDowntime,
                configs.sleeplessEffectDuration
        );
        String word = configs.hungerEnabled ? "enabled" : "disabled";
        delegator.broadcastMessage("Hunger has been " + word + ". To make this permanent, update server config.");
    }

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
            long maxTicksBeforeDowntime,
            long sleeplessEffectDuration
    ) {
    }

    public SimpleVillagerHandle(
            Delegator<ENTITY> del,
            Configs config
    ) {
        this.delegator = del;
        this.configs = config;
        this.hungerEnabled = config.hungerEnabled;
        // Registered here, not in initialize: a newly placed flag never calls initialize.
        addHungryListener(e -> {
            // At or below zero: a villager reloaded below zero must be flagged again.
            if (UtilClean.getOrDefault(this.fullness, delegator.getUUID(e), configs.baseFullness) <= 0) {
                starving.put(delegator.getUUID(e), true);
            }
        });
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, Integer> damage,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<UUID, Boolean> jobChangesPending,
            boolean hungerEnabled
    ) {
        initialize(fullness, damage, experience, level, jobChangesPending, hungerEnabled, ImmutableMap.of());
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, Integer> damage,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<UUID, Boolean> jobChangesPending,
            boolean hungerEnabled,
            ImmutableMap<UUID, JobID> guaranteedRoots
    ) {
        initialize(fullness, damage, experience, level, jobChangesPending, hungerEnabled, guaranteedRoots,
                ImmutableMap.of());
    }

    public void initialize(
            Map<UUID, Integer> fullness,
            Map<UUID, Integer> damage,
            ImmutableMap<UUID, Integer> experience,
            ImmutableMap<UUID, Integer> level,
            ImmutableMap<UUID, Boolean> jobChangesPending,
            boolean hungerEnabled,
            ImmutableMap<UUID, JobID> guaranteedRoots,
            ImmutableMap<UUID, JobID> preRolls
    ) {
        if (!this.fullness.isEmpty()) {
            throw new IllegalStateException("Attempting to initialize already initialized");
        }
        this.fullness.putAll(fullness);
        this.damage.putAll(damage);
        this.experience.putAll(experience);
        this.levels.putAll(level);
        this.jobChangesPending.putAll(jobChangesPending);
        this.guaranteedRoots.putAll(guaranteedRoots);
        this.preRolls.putAll(preRolls);
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
        // At nightfall, record which villagers have no bed.
        if (bedlessLedger.captureEdge(signals, delegator.getSleepModule().bedsKnown())) {
            List<UUID> bedless = new ArrayList<>();
            entities.forEach(e -> {
                if (!delegator.getSleepModule().hasBed(e)) {
                    bedless.add(delegator.getUUID(e));
                }
            });
            bedlessLedger.recordBedless(bedless);
            QT.FLAG_LOGGER.info("Nightfall: {} of {} townies have no bed for the night",
                    bedless.size(), entities.size());
        }
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
            if (newVal <= 0 && Math.abs(newVal) % 10 == 0) {
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
                damage, 0, e -> applyHealFactor(e, healingPerFlagTick()), 0, (newVal, e) -> {
                }
        );
    }

    /** Healing for one flag tick: one game tick's worth for each tick in the interval. */
    private int healingPerFlagTick() {
        return TICK_FACTOR * Math.toIntExact(configs.flagTickInterval);
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
                notifyStatsListeners(u);
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
                fullnessPercent, experienceNum, experienceTarget, delegator.getMood(uuid), damagePercent,
                getLevel(uuid), delegator.getMoodEffectIds(uuid));
    }

    private float getExpForCurrentLevel(UUID uuid) {
        Integer level = UtilClean.getOrDefault(levels, uuid, 1);
        Integer baseExp = configs.expRequiredAtLevel1;
        Double rampFactor = configs.expRampFactor;
        return (float) (baseExp * Math.pow(rampFactor, level - 1));
    }

    /** The villager's level; 1 by default. */
    public int getLevel(UUID uuid) {
        return UtilClean.getOrDefault(levels, uuid, 1);
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
            delegator.resetWorkStatus(villagerUUID);
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

    /** A meal: adds {@code percent} of max fullness, up to the max. Unlike {@link #fillHunger}, it never lowers it. */
    public void eat(UUID uuid, float percent) {
        int current = Util.getOrDefault(fullness, uuid, configs.baseFullness);
        float after = Math.min(1.0f, Math.max(0, current) / (float) configs.baseFullness + percent);
        fillHunger(uuid, after);
    }

    public void fillHunger(
            UUID uuid,
            float percent
    ) {
        // TODO: Get max fullness from villager
        int newHunger = (int) (percent * configs.baseFullness);
        Integer oldHunger = fullness.get(uuid);
        if (oldHunger == null || oldHunger != newHunger) {
            QT.VILLAGER_LOGGER.info("Fullness changed from {} to {} for {}", oldHunger, newHunger, uuid);
        }
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

    public void addStatsListener(UUID villager, Consumer<VillagerStatsData> l) {
        this.listeners.computeIfAbsent(villager, k -> new ArrayList<>()).add(l);
    }

    public void addAnyStatsListener(Runnable l) {
        this.anyStatsListeners.add(l);
    }

    private void notifyStatsListeners(UUID villager) {
        anyStatsListeners.forEach(Runnable::run);
        List<Consumer<VillagerStatsData>> ls = listeners.get(villager);
        if (ls == null || ls.isEmpty()) {
            return;
        }
        VillagerStatsData stats = getStats(villager);
        List.copyOf(ls).forEach(l -> l.accept(stats));
    }

    public void addHungryListener(Consumer<ENTITY> l) {
        this.hungryListeners.add(l);
    }

    public void removeStatsListener(UUID villager, Consumer<VillagerStatsData> l) {
        List<Consumer<VillagerStatsData>> ls = this.listeners.get(villager);
        if (ls != null) {
            ls.remove(l);
        }
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
        QT.FLAG_LOGGER.error(
                "Visitor mob's parent has no record of entity. Removing visitor. uuid={} handleSize={}",
                delegator.getUUID(visitorMobEntity),
                this.entities.size()
        );
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
        if (!configs.hungerEnabled) {
            return false;
        }
        return UtilClean.getOrDefault(starving, vuid, false);
    }

    public void setStarving(
            @Nullable UUID vuid,
            boolean b
    ) {
        starving.put(vuid, b);
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
        QT.FLAG_LOGGER.info("Registered villager {} with the town (arrival or reload): {}", delegator.getUUID(vEntity), vEntity);
        // One entry per villager: a reloaded entity has the same UUID.
        UUID id = delegator.getUUID(vEntity);
        // A copy still in the world wins over one that loads later.
        boolean liveCopyListed = this.entities.stream().anyMatch(
                e -> e != vEntity && delegator.getUUID(e).equals(id) && !delegator.isRemoved(e)
        );
        if (liveCopyListed) {
            QT.FLAG_LOGGER.info("Discarding a duplicate entity for townie {}: a live copy is already registered", id);
            delegator.discardEntity(vEntity);
            return;
        }
        if (this.entities.removeIf(e -> e != vEntity && delegator.getUUID(e).equals(id))) {
            QT.FLAG_LOGGER.info("Replaced a stale entity for townie {} on re-registration", id);
        }
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
            broadcastLevelUp(uuid, newLvl);
            hasBlockOfProgress.put(uuid, true);
        }
        delegator.setChanged();
    }

    private void broadcastLevelUp(
            UUID uuid,
            int newLvl
    ) {
        JobID job = getVillagerJobs().get(uuid);
        if (job == null) {
            delegator.broadcastMessage("message.villager.leveled_up", chatName(uuid), newLvl);
            return;
        }
        delegator.broadcastMessage(
                "message.villager.leveled_up_job", chatName(uuid), Jobs.getRootNameComponent(job), newLvl
        );
    }

    /** The villager's name for chat: "Villager 1", or a shortened UUID. */
    private String chatName(UUID villagerUUID) {
        String label = delegator.displayNameFor(villagerUUID);
        return label.isEmpty() ? UtilClean.truncateMiddle(villagerUUID) : label;
    }

    public boolean hasBlockOfProgress(UUID uuid) {
        return Util.getOrDefault(hasBlockOfProgress, uuid, false);
    }

    public void clearBlockOfProgress(UUID uuid) {
        hasBlockOfProgress.put(uuid, false);
        delegator.setChanged();
    }

    /** The latched villagers, for saving, so an earned Block of Progress is still deposited after a reload. */
    public ImmutableMap<UUID, Boolean> getBlockOfProgressLatches() {
        ImmutableMap.Builder<UUID, Boolean> b = ImmutableMap.builder();
        hasBlockOfProgress.forEach((uuid, latched) -> {
            if (latched) {
                b.put(uuid, true);
            }
        });
        return b.build();
    }

    public void restoreBlockOfProgress(Map<UUID, Boolean> latches) {
        hasBlockOfProgress.putAll(latches);
    }

    public Optional<JobID> scheduleJobRootChange(
            UUID villagerUUID,
            long currentTick,
            boolean instant
    ) {
        return scheduleJobRootChange(villagerUUID, currentTick, instant, Optional.empty());
    }

    /** Schedules a job change and returns the job: a quest's promised one, else a random pick. Empty when there is no candidate. */
    public Optional<JobID> scheduleJobRootChange(
            UUID villagerUUID,
            long currentTick,
            boolean instant,
            Optional<JobID> guaranteedRoot
    ) {
        ENTITY e = getEntity(villagerUUID);
        if (e == null) {
            QT.FLAG_LOGGER.error("Villager not found for job root change: {}", villagerUUID);
            return Optional.empty();
        }
        UUID vuid = delegator.getUUID(e);
        guaranteedRoot.ifPresentOrElse(
                root -> guaranteedRoots.put(vuid, root),
                () -> guaranteedRoots.remove(vuid)
        );
        if (instant) {
            QT.FLAG_LOGGER.info(
                    "Villager {} will change to a new job NOW (creative mode or onboarding)",
                    UtilClean.truncateMiddle(villagerUUID)
            );
            changeJobRootNow(e, currentTick);
            return guaranteedRoot;
        }
        // Decide the job now, at the spend; the morning applies it.
        JobID preRoll = rollPendingRoot(delegator.getJobId(e)).orElse(null);
        if (preRoll != null) {
            preRolls.put(vuid, preRoll);
        } else {
            preRolls.remove(vuid);
        }
        jobChangesPending.put(vuid, true);
        delegator.broadcastMessage("message.questown.villager.change_job_in_morning", chatName(villagerUUID));
        return guaranteedRoot.isPresent() ? guaranteedRoot : Optional.ofNullable(preRoll);
    }

    /** The job a pending change takes: one no villager has yet, else any other. Empty when there is none. */
    Optional<JobID> rollPendingRoot(JobID currentJob) {
        ImmutableSet<JobID> candidates = rootCandidates(currentJob);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(delegator.shuffle(candidates).get(0));
    }

    private ImmutableSet<JobID> rootCandidates(JobID currentJob) {
        Set<String> held = entities.stream()
                .map(e -> delegator.getJobId(e).rootId())
                .collect(Collectors.toSet());
        return RootRoll.candidates(ServerJobsRegistry.getAllRootJobs(), currentJob, held);
    }

    public void handleMorning(long tick) {
        forEach(delegator.getSleepModule()::stopSleeping);
        if (System.getenv("ENABLE_DEV_COMMANDS") != null) {
            forEach(v
                    -> QT.FLAG_LOGGER.warn("[DEV-DIAG:morning] wake-broadcast reached {} sleeping={}",
                                   delegator.getUUID(v),
                                   delegator.getSleepModule().isSleeping(v)));
        }
        makeAllTotallyHungry();
//        long tick = Util.getTick(town.getServerLevelUnsafe());
        forEach(v -> registerMostRecentDowntime(delegator.getUUID(v), tick));
        // Dawn: the sleepless effect for every villager with no bed last night.
        Set<UUID> bedless = bedlessLedger.drain();
        for (UUID uuid : bedless) {
            long until = tick + configs.sleeplessEffectDuration;
            delegator.applyMoodEffect(uuid, MoodEffects.SLEEPLESS, until);
            QT.FLAG_LOGGER.info("Villager {} was bedless last night; SLEEPLESS applied until tick {}",
                    UtilClean.truncateMiddle(uuid), until);
        }
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
        // A quest's promised job applies first.
        Optional<JobID> guaranteed = Optional.ofNullable(guaranteedRoots.remove(delegator.getUUID(v)));
        if (guaranteed.isPresent()) {
            QT.FLAG_LOGGER.info("Job-change quest dictates job root change to: {}", guaranteed.get());
            unlockAndChange(v, guaranteed.get(), currentTick);
            return;
        }

        Optional<JobID> override = delegator.getOvernightJobOverride();
        if (override.isPresent()) {
            QT.FLAG_LOGGER.info("Overriding random job root change in favor of: {}", override.get());
            unlockAndChange(v, override.get(), currentTick);
            return;
        }

        JobID preRoll = preRolls.remove(delegator.getUUID(v));
        if (preRoll != null) {
            QT.FLAG_LOGGER.info("Applying pre-rolled job root change to: {}", preRoll);
            unlockAndChange(v, preRoll, currentTick);
            return;
        }

        ImmutableSet<JobID> allOtherJobs = rootCandidates(delegator.getJobId(v));
        QT.FLAG_LOGGER.info(
                "Changing villager from {} to one of [{}]",
                delegator.getJobId(v).rootId(),
                String.join(", ", allOtherJobs.stream().map(JobID::rootId).toList())
        );
        if (allOtherJobs.isEmpty()) {
            QT.FLAG_LOGGER.error("Only one job root detected in town? This is likely a poorly configured data pack.");
            jobChangesPending.put(delegator.getUUID(v), false);
            return;
        }
        ImmutableList<JobID> shuffled = delegator.shuffle(allOtherJobs);
        JobID newJob = shuffled.get(0);
        if (delegator.getJobId(v).sameRoot(newJob)) {
            QT.logBug("Root change resulted in same root. From {} to {}.", delegator.getJobId(v), newJob);
        }
        unlockAndChange(v, newJob, currentTick);
    }

    /** Makes this villager a {@code job} worker now, as a spent Block of Progress does at dawn: unlocks it and its root's starting jobs, sets it, and posts the starter requests. */
    public void assignJob(
            UUID villagerUUID,
            JobID job,
            long currentTick
    ) {
        ENTITY v = getEntity(villagerUUID);
        if (v == null) {
            QT.FLAG_LOGGER.error("Could not find entity {} to assign job: {}", villagerUUID, job);
            return;
        }
        unlockAndChange(v, job, currentTick);
    }

    private void unlockAndChange(
            ENTITY v,
            JobID newJob,
            long currentTick
    ) {
        QT.FLAG_LOGGER.info("Changing villager from {} to {}", delegator.getJobId(v).rootId(), newJob);
        delegator.unlockJob(v, newJob);
        // The root's starting jobs (a cook: extract, fuel, a starter recipe).
        for (JobID defaultWork : delegator.getDefaultWork(newJob)) {
            delegator.unlockJob(v, defaultWork);
        }
        changeJobForVillager(delegator.getUUID(v), newJob, currentTick, true);
        // And the starter requests, so the new jobs have something to make.
        delegator.postStarterRequests(newJob);
    }

    public boolean isReadyForDowntime(
            UUID from,
            long currentTick
    ) {
        // TODO[Traits]: Consider augmenting downtime frequency for individual villagers.
        return downtimeState(from).isReadyForRest(currentTick);
    }

    /** Ready to rest with nothing else to do: only the time since the last rest counts. */
    public boolean isReadyForDowntimeIfIdle(
            UUID from,
            long currentTick
    ) {
        return downtimeState(from).isReadyForIdleRest(currentTick);
    }

    private DowntimeState downtimeState(UUID from) {
        return downtime.getOrDefault(from, DowntimeState.fresh(configs.maxTicksBeforeDowntime));
    }

    /** Records that a villager just rested. */
    public void markRested(UUID ownerUUID, long tick) {
        downtime.put(ownerUUID, downtimeState(ownerUUID).rest(tick));
    }

    /** Records that a villager just took real work, which earns a later rest. */
    public void markWorked(UUID ownerUUID, long tick) {
        downtime.put(ownerUUID, downtimeState(ownerUUID).work(tick));
    }

    public void registerMostRecentDowntime(
            UUID ownerUUID,
            long tick
    ) {
        markRested(ownerUUID, tick);
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
