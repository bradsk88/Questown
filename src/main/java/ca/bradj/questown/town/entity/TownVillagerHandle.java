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
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.*;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class TownVillagerHandle implements VillagerHolder {

    public static final TownVillagerHandlerSerializer SERIALIZER = new TownVillagerHandlerSerializer();

    // Unserialized
    private final Map<VillagerUUID, CompoundTag> customData = new HashMap<>();
    private final Map<VillagerUUID, Long> mostRecentDowntimeTick = new HashMap<>();
    private final Map<VillagerUUID, LookTarget> lookTargets = new HashMap<>();
    private final Map<VillagerUUID, LookTarget> mostRecentLookTarget = new HashMap<>();

    // Serialized
    private final Map<VillagerUUID, Boolean> jobChangesPending = new HashMap<>();

    public static void staticInit() {
        TownVillagerUIs.staticInit();
    }

    final Map<UUID, Integer> fullness = new HashMap<>();
    final Map<UUID, Integer> experience = new HashMap<>();
    final Map<UUID, Integer> levels = new HashMap<>();
    final Map<UUID, Integer> damage = new HashMap<>();
    final Map<UUID, PoseInPlace> requestedPose = new HashMap<>();
    final Map<UUID, Boolean> hasBlockOfProgress = new HashMap<>();

    public ImmutableMap<VillagerUUID, Boolean> getJobChangesPending() {
        return ImmutableMap.copyOf(jobChangesPending);
    }

    private record LookTarget(
            Entity who,
            Long untilTick
    ) {}

    final TownVillagerMoods moods = new TownVillagerMoods();

    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<Consumer<VillagerStatsData>> listeners = new ArrayList<>();
    private final List<Consumer<VisitorMobEntity>> hungryListeners = new ArrayList<>();
    private final UnsafeTown town = new UnsafeTown(getClass());

    private static final int TICK_FACTOR = 10;
    private final TownVillagerBedsHandle beds = new TownVillagerBedsHandle();
    final TownVillagerLearningHandle learning = new TownVillagerLearningHandle();

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
            e.getBrain().setMemory(MemoryModuleType.HOME, bestBed);
        });
    }

    private void tickHunger() {
        if (!Config.HUNGER_ENABLED.get()) {
            entities.forEach(e -> {
                fullness.put(e.getUUID(), Config.BASE_FULLNESS.get());
            });
            return;
        }

        Map<UUID, Integer> map = fullness;
        Integer base = Config.BASE_FULLNESS.get();
        BiConsumer<Integer, LivingEntity> then = (newVal, e) -> {
            if (newVal == 0) {
                hungryListeners.forEach(l -> l.accept((VisitorMobEntity) e));
            }
        };
        tickThing(map, base, e -> 10, then);
    }

    private void tickDamage() {
        tickThing(
                damage, 0, e -> applyHealFactor(e, 100), (newVal, e) -> {
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
            BiConsumer<Integer, LivingEntity> then
    ) {
        entities.forEach(e -> {
            UUID u = e.getUUID();
            int oldVal = map.getOrDefault(u, base);
            int newVal = Math.max(0, oldVal - amount.apply(e));
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

    @Override
    public Collection<JobID> getJobs() {
        return entities.stream().map(v -> ((VisitorMobEntity) v).getJobId()).toList();
    }

    @Override
    public ImmutableMap<VillagerUUID, JobID> getVillagerJobs() {
        Map<VillagerUUID, JobID> b = new HashMap<>();
        entities.stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> v.getVUID() != null) // TODO: Why would a villager have a null UUID?
                .forEach(v -> b.put(v.getVUID(), v.getJobId()));
        return ImmutableMap.copyOf(b);
    }

    @SuppressWarnings("removal")
    @Override
    public void changeJobForVillager(
            UUID visitorUUID,
            JobID jobID,
            boolean announce
    ) {
        changeJobForVillager(VillagerUUID.from(visitorUUID), jobID, announce);
    }

    @Override
    public void changeJobForVillager(
            VillagerUUID villagerUUID,
            JobID newJob,
            boolean announce
    ) {

        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        VisitorMobEntity f = getEntity(villagerUUID);
        if (f == null) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", villagerUUID, newJob);
            return;
        }

        if (DowntimeWork.matches(f.getJobId())) {
            registerMostRecentDowntime(villagerUUID, Util.getTick(town.getServerLevelUnsafe()));
        }

        doSetJob(villagerUUID, newJob, f);
        t.setChanged();
        if (announce) {
            t.messages.jobChanged(newJob, VillagerUUID.get(villagerUUID));
        }

        t.possibleWork.invalidate();
        t.getVillagerHandle().setJobChangePending(f.getVUID(), false);
    }

    private void doSetJob(
            VillagerUUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(ServerJobsRegistry.getInitializedJob(
                town.getServerLevelUnsafe(),
                jobName,
                f.getJobJournalSnapshot().items(),
                VillagerUUID.get(visitorUUID)
        ));
    }

    @Override
    public void changeToNextJobForVillager(
            UUID villagerUUID,
            JobID currentJob
    ) {
        this.town.getUnsafe().changeJobForVisitorFromBoard(villagerUUID, currentJob);
    }

    public Stream<LivingEntity> stream() {
        return entities.stream();
    }

    public void remove(LivingEntity visitorMobEntity) {
        this.entities.remove(visitorMobEntity);
        town.getUnsafe().setChanged();
    }

    @Override
    public void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    ) {
        TownVillagerUIs.showUI(
                sender,
                entities,
                type,
                villagerId,
                learning.getUnlockedJobs(),
                learning.getChildJobsKnownToExist(getEntity(villagerId).getJobId())
        );
    }

    @Override
    public void fillHunger(UUID uuid) {
        // TODO: Get max fullness from villager
        fillHunger(uuid, 1.0f);
    }

    @Override
    public void fillHunger(
            UUID uuid,
            float percent
    ) {
        fullness.put(uuid, (int) (percent * Config.BASE_FULLNESS.get()));
    }

    @Override
    public void makeAngry(UUID uuid) {
        // TODO: Implement happiness (happy = 100% work speed angry = 50% work speed)
    }

    void forEach(Consumer<VisitorMobEntity> c) {
        List<VisitorMobEntity> villagers = this.entities.stream()
                                                        .filter(v -> v instanceof VisitorMobEntity)
                                                        .map(v -> (VisitorMobEntity) v)
                                                        .toList();
        for (VisitorMobEntity villager : villagers) {
            c.accept(villager);
        }
    }

    public boolean isEmpty() {
        return this.entities.isEmpty();
    }

    @Override
    public long size() {
        return entities.size();
    }

    public void add(VisitorMobEntity vEntity) {
        this.entities.add(vEntity);

        ImmutableList<JobID> defaultWork = ServerJobsRegistry.getDefaultWork(vEntity.getJobId());
        for (JobID jobID : defaultWork) {
            unlockJob(vEntity.getUUID(), jobID);
        }

        learning.requestKnowledge(vEntity.getUUID(), defaultWork);

        TownFlagBlockEntity t = town.getUnsafe();
        this.beds.claim(vEntity, t);
        vEntity.addSleepListener(e -> {
            Double healFactor = t.getHealingHandle().getHealFactor(e.bedPos());
            long ticksHealed = (long) (e.duration() * healFactor);
            damage.compute(
                    vEntity.getUUID(), (id, cur) -> {
                        if (cur == null) {
                            return 0;
                        }
                        int newVal = Math.toIntExact(Math.max(0, cur - ticksHealed));
                        t.getDebugLogger(QT.VILLAGER_LOGGER, DebugLogArgument.VILLAGER_STATS).log(
                                "Villager damage changed from {} to {} after {} ticks of sleep via bed at {} with heal factor {} [{}]",
                                cur,
                                newVal,
                                e.duration(),
                                e.bedPos(),
                                healFactor,
                                vEntity.getUUID()
                        );
                        return newVal;
                    }
            );
        });
    }

    public boolean exists(VisitorMobEntity visitorMobEntity) {
        return entities.contains(visitorMobEntity);
    }

    @Override
    public void addStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.add(l);
    }

    public void addHungryListener(Consumer<VisitorMobEntity> l) {
        this.hungryListeners.add(l);
    }

    @Override
    public void removeStatsListener(Consumer<VillagerStatsData> l) {
        this.listeners.remove(l);
    }

    @Override
    public Collection<LivingEntity> entities() {
        return this.entities;
    }

    public void makeAllTotallyHungry() {
        if (!Config.HUNGER_ENABLED.get()) {
            return;
        }
        entities.forEach(e -> {
            UUID u = e.getUUID();
            fullness.put(u, 1);
            // Listeners will be notified on next tick
        });
    }

    @Override
    public boolean isDining(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(v.getUUID()))
                       .map(v -> ServerJobsRegistry.isDining(((VisitorMobEntity) v).getJobId())).findFirst()
                       .orElse(false);
    }

    @Override
    public boolean canDine(UUID uuid) {
        return entities.stream().filter(v -> uuid.equals(v.getUUID()))
                       .map(v -> ((VisitorMobEntity) v).canStopWorkingAtAnyTime()).findFirst().orElse(false);
    }

    @Override
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

    @Override
    public int getAffectedTime(
            UUID uuid,
            Integer timeToAugment
    ) {
        float offset = ((Config.NEUTRAL_MOOD.get() / 100f) - moods.getMood(uuid));
        return (int) ((1f + offset) * timeToAugment);
    }

    @Override
    public int getWorkSpeed(UUID uuid) {
        return (int) (moods.getMood(uuid) * 10);
    }

    public void associate(TownFlagBlockEntity t) {
        this.town.initialize(t);
        this.learning.associate(t);
    }

    @Override
    public void freezeVillagers(Integer ticks) {
        stream().filter(VisitorMobEntity.class::isInstance).map(VisitorMobEntity.class::cast)
                .forEach(v -> v.freeze(ticks));
    }

    @Override
    public void recallVillagers() {
        final BlockPos visitorJoinPos = town.getUnsafe().getBlockPos();
        forEach(v -> {
            QT.FLAG_LOGGER.info("Moving {} to {} and healing", v, visitorJoinPos);
            v.setPos(visitorJoinPos.getX(), visitorJoinPos.getY(), visitorJoinPos.getZ());
            v.setHealth(v.getMaxHealth());
        });
    }

    @Override
    public void validateEntity(VisitorMobEntity visitorMobEntity) {
        if (exists(visitorMobEntity)) {
            return;
        }
        QT.FLAG_LOGGER.error("Visitor mob's parent has no record of entity. Removing visitor");
        visitorMobEntity.remove(Entity.RemovalReason.DISCARDED);
    }

    /**
     * @deprecated Use VillagerUUID version
     */
    @Deprecated(forRemoval = true)
    public VisitorMobEntity getEntity(UUID ownerUUID) {
        //noinspection removal
        return getEntity(VillagerUUID.from(ownerUUID));
    }

    public VisitorMobEntity getEntity(VillagerUUID ownerUUID) {
        Optional<LivingEntity> f = stream().filter(v -> ownerUUID.matches(v.getUUID())).findFirst();
        if (f.isEmpty()) {
            QT.FLAG_LOGGER.error("No entities found for UUID: {}", ownerUUID);
            return null;
        }
        LivingEntity ff = f.get();
        if (!(ff instanceof VisitorMobEntity v)) {
            QT.FLAG_LOGGER.error("Entity is wrong type: {}", ff);
            return null;
        }
        return v;
    }

    @Override
    public void addDamage(UUID uuid) {
        Integer oldVal = Util.getOrDefault(damage, uuid, 0);
        int addition = (int) (Config.DAMAGE_TICKS.get() * TICK_FACTOR);
        damage.put(uuid, oldVal + addition);
    }

    @Override
    public int getDamageTicksLeft(UUID uuid) {
        return Util.getOrDefault(damage, uuid, 0) / TICK_FACTOR;
    }

    @Override
    public void requestPose(
            UUID ownerUUID,
            PoseInPlace pose
    ) {
        requestedPose.put(ownerUUID, pose);
    }

    @Override
    public Optional<PoseInPlace> getRequestedPose(UUID ownerUUID) {
        return Optional.ofNullable(requestedPose.get(ownerUUID));
    }

    @Override
    public void clearPoseRequests(UUID uuid) {
        requestedPose.remove(uuid);
    }

    @Override
    public void showMultiStatusUI(ServerPlayer player) {
        TownVillagerUIs.showMultiStatusUI(
                player,
                town.getUnsafe().getInfo(),
                entities,
                () -> town.getUnsafe().getAllQuestsWithRewards(),
                town.getUnsafe().getBlocksOfProgress()
        );
    }

    @Override
    public void showItemJobsUI(
            ServerPlayer sender,
            Ingredient itemToShowJobsFor
    ) {
        TownVillagerUIs.showItemJobsUI(sender, town.getUnsafe(), entities, itemToShowJobsFor);
    }

    @Override
    public void showJobUI(
            ServerPlayer sender,
            JobID jobToShow
    ) {
        TownVillagerUIs.showJobsWithSameRootUI(sender, town.getUnsafe(), entities, jobToShow);
    }

    @Override
    public void setJobChangePending(
            @Nullable VillagerUUID vuid,
            boolean value) {
        jobChangesPending.put(vuid, value);
        town.getUnsafe().setChanged();
    }

    @Override
    public boolean isJobChangePending(VillagerUUID vuid) {
        return UtilClean.getOrDefault(jobChangesPending, vuid, false);
    }

    @Override
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

    @Override
    public void unlockJob(
            UUID villagerUUID,
            JobID id
    ) {
        learning.unlockJob(villagerUUID, id);
    }

    @Override
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
            town.getUnsafe().messages.broadcastMessage("message.villager.leveled_up", uuid, newLvl);
            hasBlockOfProgress.put(uuid, true);
        }
    }

    @Override
    public boolean hasBlockOfProgress(UUID uuid) {
        return Util.getOrDefault(hasBlockOfProgress, uuid, false);
    }

    @Override
    public void clearBlockOfProgress(UUID uuid) {
        hasBlockOfProgress.put(uuid, false);
    }

    @Override
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

        TownFlagBlockEntity t = town.getUnsafe();
        t.getVillagerHandle().setJobChangePending(e.getVUID(), true);
        t.messages.broadcastMessage("message.questown.villager.leveled_up", UtilClean.truncateMiddle(villagerUUID));
    }

    @Override
    public boolean isUnlocked(JobID jobID) {
        return learning.isUnlocked(jobID);
    }

    @Override
    public UnsafeVillagerData getUnprotectedDataHandle(@Nullable VillagerUUID vuid) {
        return new UnsafeVillagerData() {
            @Override
            public String get(String key) {

                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                if (!tag.contains(key)) {
                    return null;
                }
                return tag.getString(key);
            }

            @Override
            public void write(
                    String key,
                    String value
            ) {

                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                tag.putString(key, value);
                customData.put(vuid, tag);
            }

            @Override
            public void clear(String key) {
                CompoundTag tag = UtilClean.getOrDefault(customData, vuid, new CompoundTag());
                tag.remove(key);
                customData.put(vuid, tag);
            }
        };
    }

    @Override
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

    @Override
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
        lookTargets.computeIfAbsent(vuid, (k) -> {
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
        });
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
        forEach(v -> {
            if (!isJobChangePending(v.getVUID())) {
                return;
            }
            changeJobRootNow(v);
        });
    }

    private void changeJobRootNow(VisitorMobEntity v) {
        ImmutableSet<JobID> allRoots = ServerJobsRegistry.getAllRootJobs();
        List<JobID> allOtherJobs = allRoots.stream().filter(z -> !v.getJobId().sameRoot(z)).toList();
        if (allOtherJobs.isEmpty()) {
            QT.FLAG_LOGGER.error("Only one job root detected in town? This is likely a poorly configured data pack.");
            jobChangesPending.put(v.getVUID(), false);
            return;
        }
        ImmutableList<JobID> shuffled = Compat.shuffle(
                ImmutableSet.copyOf(allOtherJobs),
                town.getServerLevelUnsafe()
        );
        JobID newJob = shuffled.get(0);
        town.getUnsafe().getVillagerHandle().unlockJob(v.getUUID(), newJob);
        town.getUnsafe().getVillagerHandle().changeJobForVillager(v.getVUID(), newJob, true);
    }

    public boolean isReadyForDowntime(
            VillagerUUID from,
            long currentTick
    ) {
        // TODO[Traits]: Consider augmenting downtime frequency for individual villagers
        Long maxTicksBeforeDowntime = Config.MAX_TICKS_BETWEEN_DOWNTIME.get();
        Long mostRecent = UtilClean.getOrDefault(mostRecentDowntimeTick, from, -maxTicksBeforeDowntime);
        return (currentTick - mostRecent) >= maxTicksBeforeDowntime;
    }

    public void registerMostRecentDowntime(
            VillagerUUID ownerUUID,
            long tick
    ) {
        mostRecentDowntimeTick.put(ownerUUID, tick);
    }
}
