package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.TimeWarpWorldInteraction.Inputs;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Warp paid no XP (dev, 2026-10-24: fix it): realtime grants XP from a completion listener on the
 * townie's own job, and the warp's world interactions are fresh objects with no listener. A warp
 * completion now records the cycle's XP on the warp state ({@code onJobCompleted}), the record
 * survives the state copies, and the settle point consumes it once ({@code TownFlagState.warp}).
 * The call site (one line in {@code AbstractWorldInteraction.tryExtractProduct}, beside the
 * completion listeners) and the settle are checked in a warp autotest's log
 * ({@code [warp] experience granted}).
 */
class TimeWarpExperienceTest {

    private static final UUID TOWNIE = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aWarpCompletion_recordsTheCyclesXpForItsTownie() {
        MCTownState town = singleVillagerTown();

        interaction(200).onJobCompleted(new Inputs(town, null, TOWNIE), town);
        interaction(200).onJobCompleted(new Inputs(town, null, TOWNIE), town);

        Assertions.assertEquals(Map.of(TOWNIE, 400), town.recordedExperience());
    }

    @Test
    void aJobWithNoXp_recordsNothing() {
        MCTownState town = singleVillagerTown();

        interaction(0).onJobCompleted(new Inputs(town, null, TOWNIE), town);

        Assertions.assertEquals(Map.of(), town.recordedExperience());
    }

    @Test
    void theRecord_survivesAStateCopy_andIsConsumedOnce() {
        MCTownState town = singleVillagerTown();
        town.recordExperience(TOWNIE, 200);

        MCTownState copied = town.setJobBlockState(new BlockPos(0, 64, 0), State.fresh());
        Assertions.assertEquals(Map.of(TOWNIE, 200), copied.recordedExperience());

        copied.clearRecordedExperience();
        Assertions.assertEquals(Map.of(), copied.recordedExperience());
        Assertions.assertEquals(Map.of(), town.recordedExperience(), "shared by reference, like the LOOT records");
    }

    private static TimeWarpWorldInteraction interaction(int xp) {
        DeclarativeJobChecks<Inputs, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks =
                new DeclarativeJobChecks<>(
                        ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
                        room -> true,
                        block -> true
                );
        return new TimeWarpWorldInteraction(
                BlockPos.ZERO, new JobID("test", "test"), 0, 50, 0, checks,
                (level, items) -> ImmutableList.of(), inputs -> null, ImmutableMap.of(),
                ImmutableList.of(), null, null
        ).withExperiencePerCompletion(xp);
    }

    private static MCTownState singleVillagerTown() {
        TownState.VillagerData<MCHeldItem> v = new TownState.VillagerData<>(
                0, 0, 0,
                new ca.bradj.questown.jobs.SimpleSnapshot<ca.bradj.questown.jobs.production.ProductionStatus, MCHeldItem>(
                        new JobID("hunter", "sword"),
                        ca.bradj.questown.jobs.production.ProductionStatus.FACTORY.idle(),
                        ImmutableList.of()),
                TOWNIE
        );
        return new MCTownState(
                List.of(v), List.of(), ImmutableMap.of(), ImmutableMap.of(), List.of(),
                ImmutableList.of(), ImmutableMap.of(), 0L
        );
    }
}
