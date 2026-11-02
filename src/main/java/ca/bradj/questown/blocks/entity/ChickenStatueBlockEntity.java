package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Block entity for the stone chicken statue, drawn by {@code ChickenStatueBlockEntityRenderer}. */
public class ChickenStatueBlockEntity extends BlockEntity {
    public ChickenStatueBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        super(TilesInit.CHICKEN_STATUE.get(), blockPos, blockState);
    }
}
