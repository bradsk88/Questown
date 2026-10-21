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
import java.util.stream.Stream;

public final class SimpleVillagerHandle<DATA, ENTITY> {

    private final Delegator<ENTITY> delegator;
    private boolean hungerEnabled = false;

    private Configs configs;

    /** Which townies had no bed for the night that just ended; drained at morning
     *  when each recorded townie gets the SLEEPLESS mood effect. */
    private final BedlessLedger bedlessLedger = new BedlessLedger();

    public Map<UUID, ? extends Set<JobID>> getUnlockedJobs() {
        return delegator.getUnlockedJobs();
    }

    public Set<JobID> getChildJobsKnownToExist(JobID jobId) {
        return delegator.getChildJobsKnownToExist(jobId);
    }

    // Unserialized
    final Map<UUID, DATA> customData = new HashMap<>();
    // Per-townie downtime state — when they last rested (null = never), whether they have
    // worked, and the town's rest threshold (n) — lives on the DowntimeState value so the
    // "rest is a reward, not a default" rule and its transitions are owned by the value, not
    // scattered here (STATE.md § Mission). A fresh / never-worked townie (worked=false) is
    // never ready-to-rest before it works — the run-19 shape. This replaces the earlier trio
    // of mostRecentDowntimeTick + restedUuids + workDoneUuids (the latter two were pure
    // presence-flags, now folded into DowntimeState).
    private final Map<UUID, DowntimeState> downtime = new HashMap<>();
    private final Map<UUID, Long> mostRecentFoodAttempt = new HashMap<>();
    private final Map<UUID, Boolean> starving = new HashMap<>();

    // Serialized
    private final Map<UUID, Boolean> jobChangesPending = new HashMap<>();
    // roots guaranteed by blessed ("gold") BOPs; cleared once the pending change applies
    private final Map<UUID, JobID> guaranteedRoots = new HashMap<>();
    // roots pre-rolled at the BOP-spend moment for ordinary (non-blessed) changes; applied at
    // morning unless a tutorial override wins; persisted across save/quit like guaranteedRoots
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
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
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
        // Nightfall edge: capture which townies have no bed for the coming night,
        // so the morning can apply the SLEEPLESS debuff to exactly those.
        if (bedlessLedger.captureEdge(signals)) {
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

    /**
     * Damage is stored as ticks-to-heal x {@link #TICK_FACTOR}, and {@code DamageTicks} promises
     * "the number of ticks it will take for one point of damage to heal" — so healing has to
     * advance by one tick's worth per <em>game</em> tick, whatever the flag interval is. This runs
     * once per flag tick, so it covers the whole interval, exactly as hunger does.
     *
     * <p>It was previously the constant 100, i.e. this expression with the interval frozen at its
     * default of 10 — which made injured townies heal 10x slower than the config claimed in any
     * world running a larger interval.
     */
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

    /**
     * The townie's villager level (default 1 - townies start un-leveled). The skills-tab
     * gate's read seam: a job becomes unlockable at {@code Work.getAvailableAtLevel()}
     * (JSON {@code available_at_level}).
     */
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

    /**
     * A meal: raises fullness by {@code percent} of max, capped at max. Unlike {@link #fillHunger},
     * which sets fullness, a meal never lowers it (2026-10-21: a raw-food meal set an 87% villager
     * to 50%).
     */
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
            QT.VILLAGER_LOGGER.info("Fullness changed from {} to {}", oldHunger, newHunger);
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
        QT.FLAG_LOGGER.info("Registered entity with town {}: {}", delegator.getUUID(vEntity), vEntity);
        // One entry per townie: a reloaded entity is a new object with the same UUID, and the
        // stale copy (unloaded with its chunk) stayed listed - the town counted the villager
        // twice and the raw-food arrival gate opened (2026-10-17 village/day1 reload step).
        UUID id = delegator.getUUID(vEntity);
        // A copy that is still in the world wins over a late arrival: after a catch-up warp,
        // recoverMobs respawns the townie with the warped state, and the pre-absence copy can
        // load with its chunk afterwards. Replacing the live copy with it dropped the warp's
        // results, and the validator then discarded the warped copy as unknown (2026-10-19).
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

    public Optional<JobID> scheduleJobRootChange(
            UUID villagerUUID,
            long currentTick,
            boolean instant
    ) {
        return scheduleJobRootChange(villagerUUID, currentTick, instant, Optional.empty());
    }

    /**
     * Schedules a root change and returns the root decided at the spend moment: the blessed
     * guarantee when the BOP carried one, otherwise a pre-roll from the registry's roots. Empty
     * when instant (applied now) or when no candidate root exists (the morning then re-rolls).
     */
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
        // A newer schedule replaces any older guarantee, even a plain (random) one.
        guaranteedRoot.ifPresentOrElse(
                root -> guaranteedRoots.put(vuid, root),
                () -> guaranteedRoots.remove(vuid)
        );
        if (instant) {
            QT.FLAG_LOGGER.info(
                    "Villager {} will change to a new job NOW (creative mode)",
                    UtilClean.truncateMiddle(villagerUUID)
            );
            changeJobRootNow(e, currentTick);
            return Optional.empty();
        }
        // Decide the outcome at spend time: an ordinary BOP pre-rolls its root now, so the
        // player's act (and any quest it satisfies) resolves immediately. The morning only
        // applies the stored root - a tutorial override may still win there.
        JobID preRoll = rollPendingRoot(delegator.getJobId(e)).orElse(null);
        if (preRoll != null) {
            preRolls.put(vuid, preRoll);
        } else {
            preRolls.remove(vuid);
        }
        jobChangesPending.put(vuid, true);
        String label = delegator.displayNameFor(villagerUUID);
        delegator.broadcastMessage(
                "message.questown.villager.change_job_in_morning",
                label.isEmpty() ? UtilClean.truncateMiddle(villagerUUID) : label
        );
        return guaranteedRoot.isPresent() ? guaranteedRoot : Optional.ofNullable(preRoll);
    }

    /**
     * The candidate roots a pending change may take: every registry root except the townie's
     * current one, randomly picked. Empty when the registry offers no alternative.
     */
    Optional<JobID> rollPendingRoot(JobID currentJob) {
        ImmutableSet<JobID> allRoots = ServerJobsRegistry.getAllRootJobs();
        List<JobID> allOtherJobs = allRoots.stream().filter(z -> !currentJob.sameRoot(z)).toList();
        if (allOtherJobs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(delegator.shuffle(ImmutableSet.copyOf(allOtherJobs)).get(0));
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
        // Dawn: apply the SLEEPLESS debuff to every townie that had no bed last night.
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
        // A blessed ("gold") BOP spent on this villager dictates the new root; it applies
        // before any overnight quest override, and ordinary random picks.
        Optional<JobID> guaranteed = Optional.ofNullable(guaranteedRoots.remove(delegator.getUUID(v)));
        if (guaranteed.isPresent()) {
            QT.FLAG_LOGGER.info("Blessed BOP dictates job root change to: {}", guaranteed.get());
            unlockAndChange(v, guaranteed.get(), currentTick);
            return;
        }

        Optional<JobID> override = delegator.getOvernightJobOverride();
        if (override.isPresent()) {
            QT.FLAG_LOGGER.info("Overriding random job root change in favor of: {}", override.get());
            unlockAndChange(v, override.get(), currentTick);
            return;
        }

        // Pre-rolled at the BOP-spend moment; beats the dawn re-roll when stored.
        JobID preRoll = preRolls.remove(delegator.getUUID(v));
        if (preRoll != null) {
            QT.FLAG_LOGGER.info("Applying pre-rolled job root change to: {}", preRoll);
            unlockAndChange(v, preRoll, currentTick);
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
        // The new root's default work (a cook: extract + fuel + the starter recipe), the same
        // set ChangeJobReward and registration unlock. Unlocking only newJob left a BOP-made
        // cook without fuel or cook_mutton (2026-10-18 playtest).
        for (JobID defaultWork : delegator.getDefaultWork(newJob)) {
            delegator.unlockJob(v, defaultWork);
        }
        changeJobForVillager(delegator.getUUID(v), newJob, currentTick, true);
        // ...and the starter requests a quest-made villager gets (ChangeJobReward): without a
        // matching board request the new root's works have nothing to produce (2026-10-19).
        delegator.postStarterRequests(newJob);
    }

    public boolean isReadyForDowntime(
            UUID from,
            long currentTick
    ) {
        // TODO[Traits]: Consider augmenting downtime frequency for individual villagers.
        // Primary readiness, decided on DowntimeState: worked since the last rest, and that rest is
        // at least `n` ticks old. A fresh / never-worked townie has no last-work tick, so is never
        // ready before it works first (the run-19 shape).
        return downtimeState(from).isReadyForRest(currentTick);
    }

    /**
     * Fallback readiness for the "no work, no fallback" case: the rest is at least `n` ticks old,
     * without requiring work-since-rest. A townie with truly nothing to do rests rather than idling
     * forever (see {@code TownFlagBlockEntity.changeJobForVisitorFromBoard}'s final gate).
     */
    public boolean isReadyForDowntimeIfIdle(
            UUID from,
            long currentTick
    ) {
        return downtimeState(from).isReadyForIdleRest(currentTick);
    }

    /** The townie's downtime state, defaulting to an unseen fresh (not-yet-ready) state. */
    private DowntimeState downtimeState(UUID from) {
        return downtime.getOrDefault(from, DowntimeState.fresh(configs.maxTicksBeforeDowntime));
    }

    /**
     * Mark a townie as having just rested (downtime exit, sleep-wake, or the NOON reset).
     * Advances the "last rest" clock on their DowntimeState; together with having worked,
     * this is what lets {@link #isReadyForDowntime(UUID, long)} return true.
     */
    public void markRested(UUID ownerUUID, long tick) {
        downtime.put(ownerUUID, downtimeState(ownerUUID).rest(tick));
    }

    /**
     * Mark a townie as having adopted into a real work job at {@code tick} (the "earned a rest by
     * working" signal). Moves their last-work tick past their last rest — that is what makes
     * {@link #isReadyForDowntime(UUID, long)} (worked since the last rest) able to return true once
     * that rest is old enough. A fresh townie that has never worked has no last-work tick.
     */
    public void markWorked(UUID ownerUUID, long tick) {
        downtime.put(ownerUUID, downtimeState(ownerUUID).work(tick));
    }

    // Retained for existing call sites that only know the old name; delegates to
    // the new state model.
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
