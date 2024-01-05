package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCTownState;
import net.minecraft.server.level.ServerLevel;

public class NoOpWarper {
    public static final Warper<ServerLevel, MCTownState> INSTANCE = (level, liveState, currentTick, ticksPassed, villagerNum) -> liveState;
}
