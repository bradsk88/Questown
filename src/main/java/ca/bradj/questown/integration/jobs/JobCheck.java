package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

public interface JobCheck {

    boolean test(
            Collection<MCHeldItem> heldItems,
            BlockState blockState,
            BlockPos block
    );
}
