package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.AbstractAdvanceTime;
import ca.bradj.questown.town.PostDowntimeWarper;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.entity.ImportantTicks;
import ca.bradj.questown.town.entity.TownFlagState;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Predicate;

/**
 * Minecraft-specific implementation of AbstractAdvanceTime.
 * This class bridges the generic advance time logic to Minecraft types.
 */
public class MCAdvanceTime extends AbstractAdvanceTime<
        MCContainer,
        MCTownItem,
        MCHeldItem,
        BlockPos,
        MCTownState,
        ServerLevel
        > {

    private final ImportantTicks.Config ticksConfig;

    public MCAdvanceTime(long maxDowntimeTicks) {
        this.ticksConfig = new ImportantTicks.Config(maxDowntimeTicks);
    }

    /**
     * Creates the WarperFactory implementation that uses PostDowntimeWarper.
     * This allows dynamic job resolution during warp.
     *
     * @param townWork      The TownFlagState.Work for job resolution
     * @param townFlagPos   Position of the town flag block
     */
    public static WarperFactory<ServerLevel, MCTownState> createWarperFactory(
            TownFlagState.Work townWork,
            BlockPos townFlagPos
    ) {
        return (work, fallbackJobID, villagerIndex) ->
                new PostDowntimeWarper(townWork, fallbackJobID, villagerIndex, townFlagPos);
    }

    @Override
    protected ImmutableList<Warper.Tick> computeImportantTicks(
            Work work,
            VillagerUUID uuid,
            JobID jobID,
            Predicate<JobID> downtimeCheck,
            long downtimeTicks,
            long ticksPassed,
            long currentTick
    ) {
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                uuid,
                jobID,
                downtimeCheck,
                ticksConfig,
                ticksPassed,
                currentTick
        );
        return result.ticks();
    }

    @Override
    protected MCTownState finalizeState(MCTownState state, long currentTick) {
        return new MCTownState(
                state.villagers,
                state.containers,
                state.workStates,
                state.workTimers,
                state.gates,
                state.knowledge(),
                state.blocksOfProgress,
                currentTick
        );
    }
}
