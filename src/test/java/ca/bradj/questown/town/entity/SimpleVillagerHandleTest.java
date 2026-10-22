package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.MoodEffects;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

class SimpleVillagerHandleTest {

    // The morning root change reaches changeJobForVillager -> DinerRawFoodWork, whose static
    // init builds an Ingredient (registry-bound). Without this the class only loaded when an
    // earlier test in the forked JVM had bootstrapped vanilla; run first, it poisoned the
    // suite (2026-10-18: 259 downstream NoClassDefFoundErrors).
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    class TestSleepModule implements VillagerSleepModule<UUID> {

        @Override
        public void tick(List<UUID> uuids) {

        }

        @Override
        public void addSleepListener(
                UUID vEntity,
                BiConsumer<TownPosition, Long> listener
        ) {

        }

        @Override
        public void claimBed(UUID vEntity) {

        }

        @Override
        public void stopSleeping(UUID uuid) {

        }

        @Override
        public boolean isSleeping(UUID e) {
            return false;
        }

        /** Townies for which hasBed returns true (test seam for the SLEEPLESS capture). */
        Set<UUID> bedded = new HashSet<>();

        @Override
        public boolean hasBed(UUID e) {
            return bedded.contains(e);
        }

        @Override
        public boolean bedsKnown() {
            return true;
        }
    }

    class TestDelegator implements Delegator<UUID> {
        List<JobID> jobChanges = new ArrayList<>();
        List<UUID> workStatusResets = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<String> broadcast = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Boolean> dining = new HashMap<>();
        JobID overnightOverride = null;
        List<JobID> unlocked = new ArrayList<>();
        List<UUID> discarded = new ArrayList<>();
        /** Entity objects (by identity) that have left the world. */
        List<UUID> removedEntities = new ArrayList<>();
        ImmutableList<JobID> defaultWork = ImmutableList.of();
        List<JobID> starterRequestsPosted = new ArrayList<>();
        private VillagerSleepModule<UUID> sleepModule = new TestSleepModule();
        /** Mood effects applied via applyMoodEffect: uuid -> (effect -> untilTick). */
        final Map<UUID, Map<net.minecraft.resources.ResourceLocation, Long>> moodEffects = new HashMap<>();

        @Override
        public JobID getJobId(UUID vEntity) {
            return new JobID("tester", "tester");
        }

        @Override
        public UUID getUUID(UUID v) {
            return v;
        }

        @Override
        public void setJob(
                UUID visitorUUID,
                JobID jobName,
                UUID entity,
                boolean announce
        ) {
            jobChanges.add(jobName);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public VillagerSleepModule<UUID> getSleepModule() {
            return sleepModule;
        }

        @Override
        public void debug(
                QT.QTLogger villagerLogger,
                String category,
                String s,
                Object... args
        ) {
            logs.add(s + " " + List.of(args));
        }

        @Override
        public boolean isUUID(
                UUID v,
                UUID ownerUUID
        ) {
            return v.equals(ownerUUID);
        }

        @Override
        public void invalidateCachedWorkPossibilities() {

        }

        @Override
        public HealingModule<UUID> getHealingModule() {
            return null;
        }

        @Override
        public void addChangeListener(
                UUID vEntity,
                Runnable runnable
        ) {

        }

        @Override
        public void broadcastMessage(
                String s,
                Object... args
        ) {
            broadcast.add(s + " " + List.of(args));
        }

        @Override
        public ImmutableList<JobID> shuffle(ImmutableSet<JobID> jobIDS) {
            return ImmutableList.copyOf(jobIDS);
        }

        @Override
        public Optional<JobID> getOvernightJobOverride() {
            return Optional.ofNullable(overnightOverride);
        }

        @Override
        public void discardEntity(UUID visitorMobEntity) {
            discarded.add(visitorMobEntity);
        }

        @Override
        public boolean isRemoved(UUID e) {
            return removedEntities.stream().anyMatch(r -> r == e);
        }

        @Override
        public boolean isDining(UUID v) {
            return UtilClean.getOrDefault(dining, v, false);
        }

        @Override
        public boolean canStopWorkingAtAnyTime(UUID v) {
            return false;
        }

        @Override
        public void freeze(
                UUID v,
                int ticks
        ) {

        }

        @Override
        public float getMood(UUID uuid) {
            return 0.5f;
        }

        @Override
        public void applyMoodEffect(
                UUID uuid,
                net.minecraft.resources.ResourceLocation effect,
                long untilTick
        ) {
            moodEffects.computeIfAbsent(uuid, u -> new HashMap<>()).put(effect, untilTick);
        }

        @Override
        public void unlockJob(
                UUID v,
                JobID newJob
        ) {
            unlocked.add(newJob);
        }

        @Override
        public ImmutableList<JobID> getDefaultWork(JobID root) {
            return defaultWork;
        }

        @Override
        public void postStarterRequests(JobID root) {
            starterRequestsPosted.add(root);
        }

        @Override
        public ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs() {
            return ImmutableMap.of();
        }

        @Override
        public ImmutableSet<JobID> getChildJobsKnownToExist(JobID jobId) {
            return ImmutableSet.of();
        }

        @Override
        public void resetWorkStatus(UUID villagerUUID) {
            workStatusResets.add(villagerUUID);
        }

        @Override
        public String displayNameFor(UUID vuid) {
            return names.getOrDefault(vuid, "");
        }
    }

    private SimpleVillagerHandle<String, UUID> handleFor(TestDelegator t, UUID villager) {
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.initialize(
                ImmutableMap.of(villager, 100),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        handle.register(villager);
        return handle;
    }

    // 2026-10-21 agent playtest: the level-up chat line read "3d51...810 reached level 3".
    @Test
    public void levelUp_chatNamesTheVillager() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        t.names.put(villager, "Villager 1");
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);

        handle.addExperience(villager, 1_000_000);

        Assertions.assertTrue(t.broadcast.contains("message.villager.leveled_up [Villager 1, 2]"), t.broadcast.toString());
    }

    // 2026-10-22 agent playtest r9: the hunter's stats page read "Lvl 1" while the flag held level 4.
    // A stats listener heard every townie's hunger tick, so an open page showed whichever townie
    // changed last (level, hunger, mood). A listener hears only the townie it was added for.
    @Test
    public void statsListener_hearsOnlyItsOwnVillager() {
        TestDelegator t = new TestDelegator();
        UUID hunter = UUID.randomUUID();
        UUID cook = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.initialize(
                ImmutableMap.of(hunter, 100, cook, 100),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(hunter, 4),
                ImmutableMap.of(),
                true
        );
        handle.register(hunter);
        handle.register(cook);
        List<Integer> heardLevels = new ArrayList<>();
        handle.addStatsListener(hunter, stats -> heardLevels.add(stats.level()));

        handle.tick(Signals.NOON);

        Assertions.assertFalse(heardLevels.isEmpty(), "the hunter's own hunger tick should notify");
        Assertions.assertEquals(List.of(4), heardLevels);
    }

    // The flag saves on any townie's stats change, so the town-wide listener still hears them all.
    @Test
    public void anyStatsListener_hearsEveryVillager() {
        TestDelegator t = new TestDelegator();
        UUID hunter = UUID.randomUUID();
        UUID cook = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.initialize(
                ImmutableMap.of(hunter, 100, cook, 100),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        handle.register(hunter);
        handle.register(cook);
        int[] heard = {0};
        handle.addAnyStatsListener(() -> heard[0]++);

        handle.tick(Signals.NOON);

        Assertions.assertEquals(2, heard[0]);
    }

    // 2026-10-21 agent playtest: a raw-food meal (hunger_fill_half) set fullness to 50% of max, so a
    // villager at 87% dropped to 50% by eating. A meal adds to fullness; it never lowers it.
    @Test
    public void eat_halfMeal_neverLowersFullness() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);
        handle.fillHunger(villager, 0.87f);

        handle.eat(villager, 0.5f);

        Assertions.assertEquals(1.0f, handle.getStats(villager).fullnessPercent(), 0.001f);
    }

    @Test
    public void eat_halfMeal_addsHalfOfMaxFullness() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);
        handle.fillHunger(villager, 0.2f);

        handle.eat(villager, 0.5f);

        Assertions.assertEquals(0.7f, handle.getStats(villager).fullnessPercent(), 0.001f);
    }

    // 2026-10-17 playtest: the gatherer left the gate mat at its terminal state; the new hunter
    // read it as its own finished hunt. A root change must drop the villager's job-block states.
    @Test
    public void changeJob_toANewRoot_resetsTheVillagersWorkStatus() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);

        handle.changeJobForVillager(villager, new JobID("hunter", "sword"), 0L, false);

        Assertions.assertEquals(List.of(villager), t.workStatusResets);
    }

    @Test
    public void changeJob_withinTheSameRoot_keepsTheVillagersWorkStatus() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);

        // TestDelegator reports the current job as tester:tester.
        handle.changeJobForVillager(villager, new JobID("tester", "other"), 0L, false);

        Assertions.assertEquals(List.of(), t.workStatusResets);
    }

    @Test
    public void testTickHungerShouldNotifyHungerListenerAfterTenTicksIfInitializedToZeroFullness() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(
                        100,
                        true,
                        1,
                        1,
                        100,
                        10,
                        100,
                        0.5f,
                        100,
                        100,
                        23000
                )
        );
        UUID villager = UUID.randomUUID();
        ImmutableMap<UUID, Integer> fullness = ImmutableMap.of(
                villager, 0
        );
        handle.initialize(
                fullness,
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        AtomicReference<UUID> hungryInformed = new AtomicReference<>(null);
        handle.register(villager);
        handle.addHungryListener(hungryInformed::set);
        for (int i = 0; i < 10; i++) {
            handle.tick(Signals.NOON);
        }
        Assertions.assertEquals(villager, hungryInformed.get());
    }
    @Test
    public void testTickHungerShouldNotNotifyHungerListenerOnTenthTick() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(
                        100,
                        true,
                        1,
                        1,
                        100,
                        10,
                        100,
                        0.5f,
                        100,
                        100,
                        23000
                )
        );
        UUID villager = UUID.randomUUID();
        ImmutableMap<UUID, Integer> fullness = ImmutableMap.of(
                villager, 11 // Will be decreased by 1 on the first tick
        );
        handle.initialize(
                fullness,
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        AtomicReference<UUID> hungryInformed = new AtomicReference<>(null);
        handle.register(villager);
        handle.addHungryListener(hungryInformed::set);
        handle.tick(Signals.NOON);
        Assertions.assertNull(hungryInformed.get());
    }

    // ---- Experience / "frozen" mechanism (Option B, finch handoff) ----
    //
    // These characterize the REAL SimpleVillagerHandle.addExperience path (not simulated):
    // experience climbs while no block-of-progress latch is set, and holds flat ("frozen")
    // once the latch is set on a level-up and is not cleared. The root cause of "experience
    // not increasing from work" is UNCONFIRMED, so these are characterization tests, not
    // reproducing tests; the completion-listener trigger that would call addExperience from a
    // job completion is untestable without a full Minecraft world (see @Disabled below).

    private static SimpleVillagerHandle<String, UUID> newHandle(TestDelegator t) {
        return new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(
                        100,   // baseFullness
                        true,  // hungerEnabled
                        1,     // flagTickInterval
                        1,     // normalBedHealMultiplier
                        100,   // expRequiredAtLevel1 (level-1 target)
                        10,    // expRampFactor (level-2 target = 100 * 10)
                        100,   // damageTicks
                        0.5f,  // neutralMood
                        100,   // bufferTicksAfterFoodAttempt
                        100,   // maxTicksBeforeDowntime
                        23000  // sleeplessEffectDuration
                )
        );
    }

    @Test
    public void addExperience_increasesWhenNoBlockOfProgress() {
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();

        handle.addExperience(v, 60);
        Assertions.assertEquals(60, handle.getStats(v).experienceValue());

        handle.addExperience(v, 20);
        Assertions.assertEquals(
                80, handle.getStats(v).experienceValue(),
                "experience climbs while no block-of-progress latch is set"
        );
    }

    @Test
    public void addExperience_holdsFlatOnceBlockOfProgressLatchSet() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();

        // 100 >= target(100) -> level up, sets the hasBlockOfProgress latch, exp -> 100 % 100 = 0.
        handle.addExperience(v, 100);
        Assertions.assertEquals(1, t.broadcast.size(), "the level-up fired exactly once");
        int frozen = handle.getStats(v).experienceValue();

        // With the latch set, further addExperience calls are no-ops: the value holds flat.
        handle.addExperience(v, 50);
        handle.addExperience(v, 50);
        Assertions.assertEquals(
                frozen, handle.getStats(v).experienceValue(),
                "experience is frozen (holds flat) while the block-of-progress latch is set"
        );
        Assertions.assertEquals(
                1, t.broadcast.size(),
                "no further level-up while the latch is set"
        );
    }

    @Disabled("Untestable without a full Minecraft world: the completion-listener path that "
            + "calls SimpleVillagerHandle.addExperience from a job completion lives in "
            + "AbstractWorldInteraction.tryExtractProduct -> jobCompletedListeners -> "
            + "VisitorMobEntity.addExperience (VisitorMobEntity ~line 385). The 'frozen' "
            + "mechanism is characterized above, but the root cause of 'experience not "
            + "increasing from work' is UNCONFIRMED (candidates: the completion listener "
            + "not firing; DeclarativeJob.getExperienceEarned returning 0; the "
            + "hasBlockOfProgress latch stuck because no item is ever inserted to clear "
            + "it; or starvation blocking work).")
    @Test
    public void completionListener_firesAddExperience_onJobCompletion() {
        // See @Disabled: cannot be reached without a real world interaction.
    }

    // ---- Blessed ("gold") BOP guarantees ----

    @Test
    public void scheduleJobRootChange_withGuarantee_forcesThatRootOnMorning() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);
        JobID guarantee = new JobID("hunter", "hunter");

        handle.scheduleJobRootChange(v, 0, false, Optional.of(guarantee));
        Assertions.assertTrue(
                handle.isJobChangePending(v),
                "a non-instant root change marks the pending flag"
        );

        handle.handleMorning(0);
        Assertions.assertEquals(
                1, t.jobChanges.size(),
                "exactly one job change applied at morning"
        );
        Assertions.assertEquals(
                guarantee, t.jobChanges.get(0),
                "the guaranteed root is applied even though the never-empty override/shuffle would not find it"
        );
        Assertions.assertTrue(
                handle.getGuaranteedRoots().isEmpty(),
                "the guarantee is consumed once applied"
        );
    }

    @Test
    public void register_replacesACopyThatLeftTheWorld() {
        // 2026-10-17: a reloaded townie is a new object with the same UUID; the copy that
        // unloaded with its chunk must not stay listed.
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID unloaded = UUID.randomUUID();
        UUID reloaded = new UUID(unloaded.getMostSignificantBits(), unloaded.getLeastSignificantBits());
        handle.register(unloaded);
        t.removedEntities.add(unloaded);

        handle.register(reloaded);

        // (ENTITY is UUID here, so membership is checked by identity, not equals.)
        Assertions.assertEquals(1, handle.size(), "one entry per townie");
        Assertions.assertSame(reloaded, handle.entities().iterator().next(), "the reloaded copy is the one listed");
        Assertions.assertTrue(t.discarded.isEmpty(), "nothing live is discarded");
    }

    @Test
    public void register_keepsALiveCopyAndDiscardsTheLateDuplicate() {
        // 2026-10-19: recoverMobs respawned the townie with its warped state; the pre-absence
        // copy loaded with its chunk afterwards and used to replace it.
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID recovered = UUID.randomUUID();
        UUID lateChunkCopy = new UUID(recovered.getMostSignificantBits(), recovered.getLeastSignificantBits());
        handle.register(recovered);

        handle.register(lateChunkCopy);

        Assertions.assertEquals(1, handle.size(), "one entry per townie");
        Assertions.assertSame(recovered, handle.entities().iterator().next(), "the live (warped) copy stays listed");
        Assertions.assertEquals(1, t.discarded.size(), "the late duplicate is discarded");
        Assertions.assertSame(lateChunkCopy, t.discarded.get(0));
    }

    @Test
    public void morningRootChange_unlocksTheNewRootsDefaultWork() {
        // 2026-10-18 playtest: a BOP-made cook got cook/extract but not fuel or cook_mutton.
        TestDelegator t = new TestDelegator();
        JobID extract = new JobID("cook", "extract");
        JobID fuel = new JobID("cook", "fuel");
        JobID mutton = new JobID("cook", "mutton");
        t.defaultWork = ImmutableList.of(extract, fuel, mutton);
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        handle.scheduleJobRootChange(v, 0, false, Optional.of(extract));
        handle.handleMorning(0);

        Assertions.assertTrue(
                t.unlocked.containsAll(List.of(extract, fuel, mutton)),
                "every default job of the new root is unlocked, got " + t.unlocked
        );
    }

    @Test
    public void morningRootChange_postsTheNewRootsStarterRequests() {
        // 2026-10-19 village/growth: a BOP-made cook got its starter work but no #villager_food
        // request on the board, so every cook job read "Product not requested" and the cook
        // idled. A quest-made cook (ChangeJobReward) gets the requests; this path must too.
        TestDelegator t = new TestDelegator();
        JobID extract = new JobID("cook", "extract");
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        handle.scheduleJobRootChange(v, 0, false, Optional.of(extract));
        handle.handleMorning(0);

        Assertions.assertEquals(List.of(extract), t.starterRequestsPosted);
    }

    @Test
    public void scheduleJobRootChange_guaranteeBeatsOvernightOverride() {
        TestDelegator t = new TestDelegator();
        t.overnightOverride = new JobID("gatherer", "gatherer");
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);
        JobID guarantee = new JobID("hunter", "hunter");

        handle.scheduleJobRootChange(v, 0, false, Optional.of(guarantee));
        handle.handleMorning(0);
        Assertions.assertEquals(
                guarantee, t.jobChanges.get(0),
                "a blessed BOP's guarantee applies before the overnight quest override"
        );
    }

    @Test
    public void scheduleJobRootChange_noGuarantee_fallsBackToOvernightOverride() {
        TestDelegator t = new TestDelegator();
        t.overnightOverride = new JobID("gatherer", "gatherer");
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        handle.scheduleJobRootChange(v, 0, false, Optional.empty());
        handle.handleMorning(0);
        Assertions.assertEquals(
                t.overnightOverride, t.jobChanges.get(0),
                "with no guarantee, the overnight override still applies"
        );
        // NB: the fully-random path (no guarantee, no override, no stored pre-roll) needs
        // ServerJobsRegistry.getAllRootJobs, which is empty headless (job data not loaded).
        // Not reachable in unit tests.
    }

    @Test
    public void plainSchedule_replacesAStaleGuarantee() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        handle.scheduleJobRootChange(v, 0, false, Optional.of(new JobID("hunter", "hunter")));
        Assertions.assertFalse(handle.getGuaranteedRoots().isEmpty());
        handle.scheduleJobRootChange(v, 0, false, Optional.empty());
        Assertions.assertTrue(
                handle.getGuaranteedRoots().isEmpty(),
                "re-scheduling without a guarantee replaces the stale one"
        );
    }

    // ---- Pre-rolled root at the BOP-spend moment (E1: the outcome settles when the quest
    // ticks, not at the morning application; the morning still wins with a tutorial override) ----

    @Test
    public void scheduleJobRootChange_blessedSpend_returnsGuaranteeAsPendingRoot() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);
        JobID guarantee = new JobID("hunter", "hunter");

        Optional<JobID> pending = handle.scheduleJobRootChange(v, 0, false, Optional.of(guarantee));

        Assertions.assertEquals(
                Optional.of(guarantee), pending,
                "a blessed spend decides its guaranteed root at the spend moment"
        );
    }

    @Test
    public void scheduleJobRootChange_ordinarySpend_noCandidateRoots_storesNothing() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        Optional<JobID> pending = handle.scheduleJobRootChange(v, 0, false, Optional.empty());

        Assertions.assertTrue(
                pending.isEmpty(),
                "with no candidate root the spend decides nothing (morning re-roll remains)"
        );
        Assertions.assertTrue(handle.getPreRolls().isEmpty(), "no pre-roll is stored");
        Assertions.assertTrue(
                handle.isJobChangePending(v),
                "the change is still pending for the morning"
        );
    }

    @Test
    public void handleMorning_appliesStoredPreRoll() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        JobID preRoll = new JobID("cook", "cook");

        // Seed the way a saved spend would: pending change + stored pre-roll.
        handle.initialize(
                Map.of(v, 10), Map.of(),
                ImmutableMap.of(), ImmutableMap.of(),
                ImmutableMap.of(v, true), true,
                ImmutableMap.of(),
                ImmutableMap.of(v, preRoll)
        );
        handle.register(v);

        handle.handleMorning(0);

        Assertions.assertEquals(1, t.jobChanges.size(), "exactly one job change applied at morning");
        Assertions.assertEquals(
                preRoll, t.jobChanges.get(0),
                "the root decided at the spend moment is applied at morning"
        );
        Assertions.assertTrue(handle.getPreRolls().isEmpty(), "the pre-roll is consumed once applied");
    }

    @Test
    public void handleMorning_overnightOverrideBeatsStoredPreRoll() {
        TestDelegator t = new TestDelegator();
        t.overnightOverride = new JobID("gatherer", "gatherer");
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        JobID preRoll = new JobID("cook", "cook");

        handle.initialize(
                Map.of(v, 10), Map.of(),
                ImmutableMap.of(), ImmutableMap.of(),
                ImmutableMap.of(v, true), true,
                ImmutableMap.of(),
                ImmutableMap.of(v, preRoll)
        );
        handle.register(v);

        handle.handleMorning(0);

        Assertions.assertEquals(1, t.jobChanges.size());
        Assertions.assertEquals(
                t.overnightOverride, t.jobChanges.get(0),
                "the tutorial override still beats the pre-roll at dawn (blessed > override > pre-roll)"
        );
    }

    // ---- Downtime "rest is a reward, not a default" state (STATE.md § Mission) ----
    //
    // `isReadyForDowntime` gates the "rest now" branch of the job cascade
    // (TownFlagBlockEntity.changeJobForVisitorFromBoard :834). If it returns true for a
    // townie that has never actually rested in this lifecycle, a freshly re-rooted
    // hunter (the run-19 6eeeefa4-at-04:38:59 shape — rootId=hunter, `dt=449`, no
    // rest record) is parked in DowntimeWork *before* the cycler can hand them a
    // real job. `markRested(uuid, tick)` is the single entry point for the "a
    // rest happened" state; both existing call sites (SVH:299 exit-DowntimeWork,
    // SVH:638 morning) now route through it.

    @Test
    public void isReadyForDowntime_freshTownie_isNotReady() {
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);

        // A townie that has never worked in this lifecycle is not ready for a rest —
        // rest is a reward for work, not a default. The run-19 6eeeefa4-at-04:38:59
        // shape: just re-rooted to hunter, no work done, no DowntimeState (fresh: worked=false).
        // The old `getOrDefault(tick, from, -max)` default made `(now - (-max)) >= max` always
        // true ⇒ rest; the record's worked=false gate removes that false default.
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, 10_000L),
                "a townie that has never worked is not ready for downtime (rest is a reward, not a default)"
        );

        // Even after a rest event, a townie that still has not worked is not ready —
        // the workDone check is the load-bearing one.
        handle.markRested(v, 0L);
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, 10_000L),
                "a rest event without subsequent work does not make a townie ready again"
        );
    }

    @Test
    public void isReadyForDowntime_afterEnoughTicksSinceWorkedAndRested_isReady() {
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);

        long maxTicks = 100L; // matches Configs(…, 100 /* maxTicksBeforeDowntime */) in newHandle()
        handle.markRested(v, 0L);
        // Tired only after working: rest at 0, work at 90 (10 ticks before threshold),
        // ready at 100 (10 ticks of work since the last rest — but we define this as
        // "enough ticks since last rest", so at 100 they are exactly at the threshold).
        handle.markWorked(v, 90L);

        Assertions.assertFalse(
                handle.isReadyForDowntime(v, maxTicks - 1),
                "one tick short of the downtime threshold is not ready"
        );
        Assertions.assertTrue(
                handle.isReadyForDowntime(v, maxTicks),
                "at the downtime threshold — having worked — the townie is ready to rest"
        );
        Assertions.assertTrue(
                handle.isReadyForDowntime(v, maxTicks * 10),
                "well past the rest the townie is still ready"
        );
    }

    @Test
    public void isReadyForDowntime_justRested_isNotReady() {
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);

        long maxTicks = 100L;
        long now = 5_000L;
        // A townie that just rested (at `now`) and then worked is not ready again until
        // `maxTicks` more ticks have passed.
        handle.markRested(v, now);
        handle.markWorked(v, now + 10L);
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, now + 1),
                "immediately after a rest the townie is not ready again"
        );
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, now + maxTicks - 1),
                "still not ready one tick before the threshold"
        );
    }

    @Test
    public void registerMostRecentDowntime_delegatesToTheSameStateAsMarkRested() {
        // The legacy name (registerMostRecentDowntime) now routes through markRested;
        // it must update the same rested state so the two paths are behaviorally
        // equivalent (both mark the townie ready at the same threshold tick, if they
        // have worked).
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID viaMark = UUID.randomUUID();
        UUID viaLegacy = UUID.randomUUID();
        handle.register(viaMark);
        handle.register(viaLegacy);
        long threshold = 100L;

        handle.markRested(viaMark, 0L);
        handle.markWorked(viaMark, 50L);
        handle.registerMostRecentDowntime(viaLegacy, 0L);
        handle.markWorked(viaLegacy, 50L);

        Assertions.assertTrue(handle.isReadyForDowntime(viaMark, threshold),
                "markRested makes the townie ready at the threshold (if they have worked)");
        Assertions.assertTrue(handle.isReadyForDowntime(viaLegacy, threshold),
                "legacy registerMostRecentDowntime updates the same state as markRested");
        Assertions.assertFalse(handle.isReadyForDowntime(viaMark, threshold - 1),
                "one tick short of the threshold is not ready");
        Assertions.assertFalse(handle.isReadyForDowntime(viaLegacy, threshold - 1),
                "one tick short of the threshold is not ready");
    }

    @Test
    public void isReadyForDowntime_noWorkDoneNeverReady() {
        // A townie that has a rest record but has **never worked** (the run-19 shape
        // for 6eeeefa4: re-rooted to hunter, NOON handler marked them rested, they
        // never produced a single item) is not ready for any amount of time.
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);
        // Simulate the NOON handler marking them rested, as it would in production.
        handle.registerMostRecentDowntime(v, 0L);
        // Even a long time later, with no work, they are not ready for rest —
        // because they haven't earned one.
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, 100_000L),
                "a townie that has a rest record but has never worked is not ready (even after a long time)"
        );
        // …and once they work, they become ready (the reward-for-work rule).
        handle.markWorked(v, 10L);
        Assertions.assertTrue(
                handle.isReadyForDowntime(v, 100_000L),
                "after a rest event **and** work the townie becomes ready"
        );
    }

    @Test
    public void isReadyForDowntimeIfIdle_fresh_isNotReady() {
        // A townie that never rested has no last-rest anchor — even the idle fallback does not apply.
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);
        Assertions.assertFalse(
                handle.isReadyForDowntimeIfIdle(v, 10_000L),
                "a fresh townie has not rested, so the idle fallback is off"
        );
    }

    @Test
    public void isReadyForDowntimeIfIdle_restedButNeverWorked_isReadyAfterThreshold() {
        // The escape: a townie that has rested but has NOT worked since (so the primary rule never
        // fires for them) can still rest when there is truly no work, once the rest is old enough.
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);
        long maxTicks = 100L;
        handle.markRested(v, 0L); // rested, never worked: primary is never ready
        Assertions.assertFalse(handle.isReadyForDowntime(v, 10_000L), "primary needs work since the rest");
        Assertions.assertFalse(
                handle.isReadyForDowntimeIfIdle(v, maxTicks - 1),
                "just rested: the idle fallback is not ready yet"
        );
        Assertions.assertTrue(
                handle.isReadyForDowntimeIfIdle(v, maxTicks),
                "rested long enough with truly no work -> rest instead of idling forever"
        );
    }

    // ---- SLEEPLESS: the dawn debuff for townies who had no bed last night ----

    @Test
    public void handleMorning_appliesSleeplessToTowniesWithoutBedLastNight() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID bedless = UUID.randomUUID();
        UUID bedded = UUID.randomUUID();
        ((TestSleepModule) t.sleepModule).bedded.add(bedded);
        handle.register(bedless);
        handle.register(bedded);

        // The day passes and the night falls: the EVENING edge captures the bedless set.
        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);

        // Dawn of the next day.
        handle.handleMorning(24000L);

        Assertions.assertEquals(
                24000L + 23000L,
                t.moodEffects.get(bedless).get(MoodEffects.SLEEPLESS),
                "the bedless townie gets SLEEPLESS expiring after its configured duration"
        );
        Assertions.assertFalse(
                t.moodEffects.getOrDefault(bedded, Map.of()).containsKey(MoodEffects.SLEEPLESS),
                "the townie with an assigned bed gets no SLEEPLESS"
        );
    }

    @Test
    public void sleeplessCapture_rearmsOnTheNextNight() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID bedless = UUID.randomUUID();
        handle.register(bedless);

        // Night one: capture at the evening edge, apply at the next morning.
        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);
        handle.handleMorning(24000L);
        Assertions.assertEquals(47000L, t.moodEffects.get(bedless).get(MoodEffects.SLEEPLESS));

        // Night two: the townie is still bedless, so the capture re-arms and the
        // second dawn overwrites the first application with a fresh expiry.
        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);
        handle.handleMorning(48000L);
        Assertions.assertEquals(
                48000L + 23000L,
                t.moodEffects.get(bedless).get(MoodEffects.SLEEPLESS),
                "a second consecutive bedless night re-applies SLEEPLESS with a new expiry"
        );
    }

    @Test
    public void handleMorning_noBedlessTownies_appliesNothing() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID bedded = UUID.randomUUID();
        ((TestSleepModule) t.sleepModule).bedded.add(bedded);
        handle.register(bedded);

        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);
        handle.handleMorning(24000L);

        Assertions.assertTrue(
                t.moodEffects.isEmpty(),
                "no mood effect is applied when every townie had a bed"
        );
    }
}