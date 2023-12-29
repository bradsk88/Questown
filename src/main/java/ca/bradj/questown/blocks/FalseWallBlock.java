package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FalseWallBlock extends Block {
    public static final String ID = "false_wall_block";
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);


    public FalseWallBlock() {
        super(Properties
                .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                .strength(1.0F, 10.0F).
                noCollission());
    }
    @Override
    public boolean propagatesSkylightDown(BlockState p_49928_, BlockGetter p_49929_, BlockPos p_49930_) {
        return true;
    }
}
