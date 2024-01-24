package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class PlateBlock extends Block implements StatefulJobBlock {
    public static final String ITEM_ID = "plate_block";

    public static final IntegerProperty STATE = IntegerProperty.create(
            "qtstate", 0, 1
    );

    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D);

    public PlateBlock(
    ) {
        super(
                Properties
                        .of(Material.GLASS, MaterialColor.TERRACOTTA_WHITE)
                        .strength(1.0F, 10.0F)
                        .noOcclusion()
        );
        registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(STATE, 0)
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
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

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // The drop is handled by Entity.dropWhenOrphaned
        return ImmutableList.of(
                ItemsInit.PLATE_BLOCK.get().getDefaultInstance()
        );
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().
                setValue(FACING, ctx.getHorizontalDirection().getOpposite()).
                setValue(STATE, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING, STATE);
    }

    @Override
    public boolean propagatesSkylightDown(
            BlockState p_49928_,
            BlockGetter p_49929_,
            BlockPos p_49930_
    ) {
        return true;
    }

    @Override
    public void setProcessingState(
            ServerLevel sl,
            BlockPos pp,
            AbstractWorkStatusStore.State bs
    ) {
        if (bs.processingState() > 1) {
            bs = bs.setProcessing(0);
        }
        BlockState s = sl.getBlockState(pp);
        s = s.setValue(STATE, bs.processingState());
        sl.setBlockAndUpdate(pp, s);
    }
}
