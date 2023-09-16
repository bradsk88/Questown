package ca.bradj.questown.blocks;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface ScheduledBlock {
    @Nullable BlockState tryAdvance(
            BlockState blockState,
            int dayTime
    );
}
