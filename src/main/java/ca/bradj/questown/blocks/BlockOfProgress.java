package ca.bradj.questown.blocks;

import ca.bradj.questown.items.QTNBT;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockOfProgress extends Block {
    public static final String ITEM_ID = "block_of_progress";

    protected static final VoxelShape SHAPE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    public BlockOfProgress(
    ) {
        super(Properties.of(Material.GLASS, MaterialColor.SNOW).strength(1.0F, 10.0F).noOcclusion());
    }

    @Override
    public VoxelShape getShape(
            BlockState p_60555_,
            BlockGetter p_60556_,
            BlockPos p_60557_,
            CollisionContext p_60558_
    ) {
        return SHAPE;
    }

    public static void bless(ItemStack item) {
        QTNBT.putBoolean(item.getOrCreateTag(), "blessed", true);
    }

    public static boolean isBlessed(ItemStack item) {
        return QTNBT.getBoolean(item.getOrCreateTag(), "blessed", false);
    }
}
