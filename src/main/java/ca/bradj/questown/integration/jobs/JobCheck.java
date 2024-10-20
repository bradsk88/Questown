package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.function.Function;

public interface JobCheck {

    boolean test(
            Collection<MCHeldItem> heldItems,
            Function<BlockPos, BlockState> blockState,
            BlockPos block
    );
}
