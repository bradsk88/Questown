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
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

class SimpleVillagerHandleTest {

    // The morning job change loads a diner class that needs vanilla bootstrapped.
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
        int changes = 0;
        Map<UUID, Boolean> dining = new HashMap<>();
        JobID overnightOverride = null;
        List<JobID> unlocked = new ArrayList<>();
        List<UUID> discarded = new ArrayList<>();
        List<UUID> removedEntities = new ArrayList<>();
        ImmutableList<JobID> defaultWork = ImmutableList.of();
        List<JobID> starterRequestsPosted = new ArrayList<>();
        private VillagerSleepModule<UUID> sleepModule = new TestSleepModule();
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
            changes++;
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

    @Test
    public void levelUp_chatNamesTheVillager() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        t.names.put(villager, "Villager 1");
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);

        handle.addExperience(villager, 1_000_000);

        Assertions.assertTrue(
                t.broadcast.stream().anyMatch(b -> b.startsWith("message.villager.leveled_up_job [Villager 1, ")),
                t.broadcast.toString()
        );
    }

    @Test
    public void levelUp_chatNamesTheJob() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        t.names.put(villager, "Villager 1");
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);

        handle.addExperience(villager, 1_000_000);

        String line = t.broadcast.stream().filter(b -> b.startsWith("message.villager.leveled_up_job")).findFirst().orElse("");
        Assertions.assertTrue(line.contains("jobs.tester"), t.broadcast.toString());
        Assertions.assertTrue(line.endsWith(", 2]"), line);
    }

    // The earned Block of Progress latch survives a save and load.
    @Test
    public void blockOfProgressLatch_survivesSaveAndLoad() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> before = handleFor(t, villager);
        before.addExperience(villager, 1_000_000);
        Assertions.assertTrue(before.hasBlockOfProgress(villager));

        SimpleVillagerHandle<String, UUID> after = handleFor(new TestDelegator(), villager);
        after.restoreBlockOfProgress(before.getBlockOfProgressLatches());

        Assertions.assertTrue(after.hasBlockOfProgress(villager));
    }

    // A level-up marks the flag changed, so it saves.
    @Test
    public void levelUp_marksTheTownChanged() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);
        int before = t.changes;

        handle.addExperience(villager, 1_000_000);

        Assertions.assertTrue(t.changes > before);
    }

    @Test
    public void blockOfProgressLatches_holdOnlyLatchedVillagers() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = handleFor(t, villager);
        handle.addExperience(villager, 1_000_000);
        handle.clearBlockOfProgress(villager);

        Assertions.assertEquals(ImmutableMap.of(), handle.getBlockOfProgressLatches());
    }

    // A stats listener hears only its own villager.
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

    // The town-wide listener hears every villager.
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

    // A meal never lowers fullness.
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

    // A job change drops the villager's job-block states.
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

        handle.changeJobForVillager(villager, new JobID("tester", "other"), 0L, false);

        Assertions.assertEquals(List.of(), t.workStatusResets);
    }

    // A villager reloaded below zero fullness reads as starving again.
    @Test
    public void reloadedBelowZero_becomesStarvingAtTheNextHungerBroadcast() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.initialize(
                ImmutableMap.of(villager, -5),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        handle.register(villager);
        Assertions.assertFalse(handle.isStarving(villager));
        for (int i = 0; i < 5; i++) {
            handle.tick(Signals.NOON);
        }
        Assertions.assertTrue(handle.isStarving(villager));
    }

    /** In a new town, a fed villager that goes hungry is flagged starving. */
    @Test
    public void freshTown_fedTownieBecomesStarvingWhenItsLiveFullnessDropsBelowZero() {
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.initialize(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                true
        );
        handle.register(villager);
        handle.fillHunger(villager, 0.05f);
        Assertions.assertFalse(handle.isStarving(villager));
        for (int i = 0; i < 15; i++) {
            handle.tick(Signals.NOON);
        }
        Assertions.assertTrue(handle.isStarving(villager));
    }

    @Test
    public void freshlyPlacedFlag_neverInitialized_fedTownieStillBecomesStarving() {
        // A newly placed flag never calls initialize, so the listener can't live there.
        TestDelegator t = new TestDelegator();
        UUID villager = UUID.randomUUID();
        SimpleVillagerHandle<String, UUID> handle = new SimpleVillagerHandle<>(
                t,
                new SimpleVillagerHandle.Configs(100, true, 1, 1, 100, 10, 100, 0.5f, 100, 100, 23000)
        );
        handle.register(villager);
        handle.fillHunger(villager, 0.05f);
        Assertions.assertFalse(handle.isStarving(villager));
        for (int i = 0; i < 15; i++) {
            handle.tick(Signals.NOON);
        }
        Assertions.assertTrue(handle.isStarving(villager));
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

    // Experience: it climbs until a level-up sets the Block of Progress latch, then holds until the latch clears.

    private static SimpleVillagerHandle<String, UUID> newHandle(TestDelegator t) {
        return new SimpleVillagerHandle<>(
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

        // Reaching the target levels up and sets the latch.
        handle.addExperience(v, 100);
        Assertions.assertEquals(1, t.broadcast.size(), "the level-up fired exactly once");
        int frozen = handle.getStats(v).experienceValue();

        // With the latch set, more experience does nothing.
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
    public void scheduleJobRootChange_instantWithGuarantee_appliesNowAndReturnsTheRoot() {
        // An onboarding spend applies at once.
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);
        JobID cook = new JobID("cook", "extract");

        Optional<JobID> pending = handle.scheduleJobRootChange(v, 0, true, Optional.of(cook));

        Assertions.assertEquals(Optional.of(cook), pending);
        Assertions.assertEquals(List.of(cook), t.jobChanges, "applied at the spend, not at morning");
        Assertions.assertFalse(handle.isJobChangePending(v));
        Assertions.assertTrue(handle.getGuaranteedRoots().isEmpty(), "the guarantee is consumed");
    }

    @Test
    public void register_replacesACopyThatLeftTheWorld() {
        // A reloaded villager is a new object with the same UUID; the old copy isn't kept.
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID unloaded = UUID.randomUUID();
        UUID reloaded = new UUID(unloaded.getMostSignificantBits(), unloaded.getLeastSignificantBits());
        handle.register(unloaded);
        t.removedEntities.add(unloaded);

        handle.register(reloaded);

        Assertions.assertEquals(1, handle.size(), "one entry per townie");
        Assertions.assertSame(reloaded, handle.entities().iterator().next(), "the reloaded copy is the one listed");
        Assertions.assertTrue(t.discarded.isEmpty(), "nothing live is discarded");
    }

    @Test
    public void register_keepsALiveCopyAndDiscardsTheLateDuplicate() {
        // A copy loading after the warped one doesn't replace it.
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
        // A cook made with a Block of Progress gets its root's starting jobs.
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
        // And the food request on the board.
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
    public void assignJob_makesTheSameWorkerAsARootChange() {
        // Assigning a job does what a Block of Progress job change does.
        TestDelegator t = new TestDelegator();
        JobID extract = new JobID("cook", "extract");
        JobID fuel = new JobID("cook", "fuel");
        JobID mutton = new JobID("cook", "mutton");
        t.defaultWork = ImmutableList.of(extract, fuel, mutton);
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID v = UUID.randomUUID();
        handle.register(v);

        handle.assignJob(v, mutton, 0);

        Assertions.assertTrue(
                t.unlocked.containsAll(List.of(extract, fuel, mutton)),
                "every default job of the root is unlocked, got " + t.unlocked
        );
        Assertions.assertEquals(List.of(mutton), t.jobChanges);
        Assertions.assertEquals(List.of(mutton), t.starterRequestsPosted);
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

    // The job is rolled at the spend; the morning applies it.

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

        // Set up as a saved spend would be.
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

    // Rest is earned by working.

    @Test
    public void isReadyForDowntime_freshTownie_isNotReady() {
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);

        // Never worked: not ready to rest.
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, 10_000L),
                "a townie that has never worked is not ready for downtime (rest is a reward, not a default)"
        );

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

        long maxTicks = 100L;
        handle.markRested(v, 0L);
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
        // Just rested: not ready again until maxTicks have passed.
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
        // The older name goes through markRested.
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
        // Rested but never worked: never ready.
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);
        handle.registerMostRecentDowntime(v, 0L);
        Assertions.assertFalse(
                handle.isReadyForDowntime(v, 100_000L),
                "a townie that has a rest record but has never worked is not ready (even after a long time)"
        );
        handle.markWorked(v, 10L);
        Assertions.assertTrue(
                handle.isReadyForDowntime(v, 100_000L),
                "after a rest event **and** work the townie becomes ready"
        );
    }

    @Test
    public void isReadyForDowntimeIfIdle_fresh_isNotReady() {
        // Never rested: the idle fallback doesn't apply either.
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
        // Rested, not worked since: with no work, it rests once the rest is old enough.
        SimpleVillagerHandle<String, UUID> handle = newHandle(new TestDelegator());
        UUID v = UUID.randomUUID();
        handle.register(v);
        long maxTicks = 100L;
        handle.markRested(v, 0L);
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

    @Test
    public void handleMorning_appliesSleeplessToTowniesWithoutBedLastNight() {
        TestDelegator t = new TestDelegator();
        SimpleVillagerHandle<String, UUID> handle = newHandle(t);
        UUID bedless = UUID.randomUUID();
        UUID bedded = UUID.randomUUID();
        ((TestSleepModule) t.sleepModule).bedded.add(bedded);
        handle.register(bedless);
        handle.register(bedded);

        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);

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

        handle.tick(Signals.MORNING);
        handle.tick(Signals.NOON);
        handle.tick(Signals.EVENING);
        handle.tick(Signals.NIGHT);
        handle.handleMorning(24000L);
        Assertions.assertEquals(47000L, t.moodEffects.get(bedless).get(MoodEffects.SLEEPLESS));

        // Still bedless the second night: a fresh expiry.
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