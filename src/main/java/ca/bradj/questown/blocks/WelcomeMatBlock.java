package ca.bradj.questown.blocks;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class WelcomeMatBlock extends HorizontalDirectionalBlock {
    public static final String ITEM_ID = "welcome_mat_block";
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public WelcomeMatBlock(
    ) {
        super(
                BlockBehaviour.Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
    }

    public static void StoreParentOnNBT(
            ItemStack itemInHand,
            BlockPos p
    ) {
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_x", Questown.MODID), p.getX());
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_y", Questown.MODID), p.getY());
        itemInHand.getOrCreateTag().putInt(String.format("%s.parent_pos_z", Questown.MODID), p.getZ());
    }

    public static @Nullable BlockPos GetParentFromNBT(
            ServerLevel level,
            ItemStack itemInHand
    ) {
        if (itemInHand.getTag() == null) {
            return null;
        }
        int x, y, z;
        String xTag = String.format("%s.parent_pos_x", Questown.MODID);
        if (!itemInHand.getTag().contains(xTag)) {
            return null;
        }
        String yTag = String.format("%s.parent_pos_y", Questown.MODID);
        if (!itemInHand.getTag().contains(yTag)) {
            return null;
        }
        String zTag = String.format("%s.parent_pos_z", Questown.MODID);
        if (!itemInHand.getTag().contains(zTag)) {
            return null;
        }
        x = itemInHand.getOrCreateTag().getInt(xTag);
        y = itemInHand.getOrCreateTag().getInt(yTag);
        z = itemInHand.getOrCreateTag().getInt(zTag);


        BlockPos bp = new BlockPos(x, y, z);
        Optional<TownFlagBlockEntity> oEntity = level.getBlockEntity(bp, TilesInit.TOWN_FLAG.get());
        return oEntity.map(TownFlagBlockEntity::getTownFlagBasePos).orElse(null);
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

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        ItemStack item = ctx.getItemInHand();
        @Nullable BlockPos parent = GetParentFromNBT(sl, item);

        if (parent == null) {
            throw new IllegalStateException("Welcome mat has no parent");
        }

        Optional<TownFlagBlockEntity> oEntity = ctx.getLevel().getBlockEntity(parent, TilesInit.TOWN_FLAG.get());
        BlockPos matPos = ctx.getClickedPos();
        if (oEntity.isEmpty()) {
            Questown.LOGGER.error("Parent no longer exists. Welcome mat is not disconnected at {}", matPos);
        } else {
            oEntity.get().registerWelcomeMat(matPos);
        }
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }
}
