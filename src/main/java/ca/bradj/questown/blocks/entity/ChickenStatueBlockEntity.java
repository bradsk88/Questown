package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holder block entity for the helper-chicken onboarding trophy. The block's
 * own model is empty; {@code ChickenStatueBlockEntityRenderer} draws the
 * stone-colored chicken model over the block, so a completed chicken reads
 * as a pickable stone statue rather than a stone cube.
 */
public class ChickenStatueBlockEntity extends BlockEntity {
    public ChickenStatueBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        super(TilesInit.CHICKEN_STATUE.get(), blockPos, blockState);
    }
}
