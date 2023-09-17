package ca.bradj.questown.blocks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface ScheduledBlock {
    @Nullable BlockState tryAdvance(
            ServerLevel level,
            BlockState blockState
    );
}
