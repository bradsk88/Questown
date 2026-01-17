package ca.bradj.questown.town.entity;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.town.AbstractAdvanceTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Minecraft-specific implementation of AbstractAdvanceTime.
 * This class provides the concrete implementations needed for time warp
 * in a Minecraft environment.
 */
public class MCAdvanceTime extends AbstractAdvanceTime<
        MCContainer,
        MCTownItem,
        MCHeldItem,
        BlockPos,
        MCTownState,
        ServerLevel
        > {

    @Override
    protected MCTownState finalizeState(MCTownState state, long currentTick) {
        // Create a new state with the current tick as the reference time
        return new MCTownState(
                state.villagers,
                state.containers,
                state.workStates,
                state.workTimers,
                state.gates,
                state.knowledge(),
                state.blocksOfProgress,
                currentTick,
                state.getInsertedItems()
        );
    }
}
