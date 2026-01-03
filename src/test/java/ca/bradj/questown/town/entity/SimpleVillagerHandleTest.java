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
            return Optional.empty();
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

}