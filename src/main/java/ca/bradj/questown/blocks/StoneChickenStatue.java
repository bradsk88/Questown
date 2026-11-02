package ca.bradj.questown.blocks;

import ca.bradj.questown.blocks.entity.ChickenStatueBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The stone chicken left behind when the helper chicken's lessons end. Breaks by hand and drops itself. */
public class StoneChickenStatue extends Block implements EntityBlock {
    public static final String ITEM_ID = "stone_chicken_statue";

    public StoneChickenStatue() {
        super(BlockBehaviour.Properties
                .of()
                .strength(0.0f)
                .noOcclusion());
    }

    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new ChickenStatueBlockEntity(pos, state);
    }
}
