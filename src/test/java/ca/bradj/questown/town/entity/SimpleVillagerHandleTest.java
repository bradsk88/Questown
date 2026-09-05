package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.rooms.TownPosition;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

class SimpleVillagerHandleTest {

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
    }

    class TestDelegator implements Delegator<UUID> {
        List<JobID> jobChanges = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<String> broadcast = new ArrayList<>();
        Map<UUID, Boolean> dining = new HashMap<>();
        JobID overnightOverride = null;
        private VillagerSleepModule<UUID> sleepModule = new TestSleepModule();

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
        public void unlockJob(
                UUID v,
                JobID newJob
        ) {

        }

        @Override
        public ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs() {
            return ImmutableMap.of();
        }

        @Override
        public ImmutableSet<JobID> getChildJobsKnownToExist(JobID jobId) {
            return ImmutableSet.of();
        }
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
                        100
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
                        100
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
                        100    // maxTicksBeforeDowntime
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
        // NB: the fully-random path (no guarantee, no override) needs ServerJobsRegistry.getAllRootJobs,
        // which is uninitialized headless (Works.works null → NPE). Not reachable in unit tests.
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
}