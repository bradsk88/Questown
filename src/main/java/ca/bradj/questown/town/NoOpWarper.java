package ca.bradj.questown.town;

import ca.bradj.questown.integration.minecraft.MCTownState;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

public class NoOpWarper {
    public static final Warper<ServerLevel, MCTownState> INSTANCE = new Warper<ServerLevel, MCTownState>() {
        @Override
        public MCTownState warp(
                ServerLevel level,
                MCTownState liveState,
                long currentTick,
                long ticksPassed,
                int villagerNum
        ) {
            throw new UnsupportedOperationException("NoOpWarper should never be run");
        }

        @Override
        public Collection<Tick> getTicks(
                long referenceTick,
                long ticksPassed
        ) {
            return ImmutableList.of();
        }
    };
}
