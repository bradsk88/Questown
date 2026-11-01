package ca.bradj.questown.town;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.SimpleSnapshot;
import ca.bradj.questown.jobs.Signals.DayTime;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.entity.TownFlagState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link PostDowntimeWarper}'s NoOp-warper guard: a "seeking work" / no-work job's warper is
 * {@link NoOpWarper#INSTANCE}, an anonymous {@link Warper} (not a {@code NoOpWarper}), so the guard
 * must compare against the instance; otherwise {@code runToCompletion} runs the throwing warper and
 * {@link UnsupportedOperationException} aborts the whole town warp. Drives the real
 * {@link PostDowntimeWarper#warp} (the {@code advanceTime} loop cannot reach this guard).
 */
class PostDowntimeWarperNoOpGuardTest {

    /** A "seeking work" job: {@code getWarper} returns {@link NoOpWarper#INSTANCE} for it. */
    private static final JobID SEEKING_WORK = new JobID("crafter", WorkSeekerJob.WORK_ID);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noOpWarper_isGuarded_notRun() {
        // Arrange — one villager, no rooms (so findAssignedFurnace never dereferences the level),
        // and a work source that resolves no preselected job, so the fallback (seeking work) is used.
        MCTownState liveState = singleVillagerTown();
        PostDowntimeWarper warper = new PostDowntimeWarper(
                new NoWorkFlagStateWork(),
                SEEKING_WORK,
                0,
                new BlockPos(0, 64, 0),
                List.of(),
                null
        );

        // Act & Assert: the guard must turn the throwing NoOp warper into a no-op and return the
        // live state unchanged.
        MCTownState result = assertDoesNotThrow(
                () -> warper.warp(null, liveState, 1L, 1L, 0),
                "NoOp warper must be guarded, not run"
        );
        assertSame(liveState, result, "guarded NoOp warper returns the live state unchanged");
    }

    private static MCTownState singleVillagerTown() {
        TownState.VillagerData<MCHeldItem> v = new TownState.VillagerData<>(
                0, 0, 0,
                new SimpleSnapshot<ProductionStatus, MCHeldItem>(SEEKING_WORK, ProductionStatus.FACTORY.idle(), ImmutableList.of()),
                UUID.randomUUID()
        );
        return new MCTownState(
                List.of(v),
                List.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                List.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                0L
        );
    }

    /** A TownFlagState.Work that resolves no preselected jobs, so the fallback is used. */
    private static final class NoWorkFlagStateWork implements TownFlagState.Work {
        @Override
        public void recomputeNow() {
        }

        @Override
        public JobID getRandomFinishableWork(JobID jobID, DayTime dayTime, long ticksElapsed) {
            return null;
        }

        @Override
        public List<JobID> getPreselectedJobs(JobID currentJob) {
            return List.of();
        }

        @Override
        public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
            return 0;
        }

        @Override
        public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
            return 0;
        }
    }
}
