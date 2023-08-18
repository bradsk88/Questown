package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
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
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WelcomeMatBlock extends HorizontalDirectionalBlock {
    public static final String ITEM_ID = "welcome_mat_block";
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public WelcomeMatBlock(
    ) {
        super(
                BlockBehaviour.Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
                        .noCollission()
        );
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        return ImmutableList.of(ItemsInit.WELCOME_MAT_BLOCK.get().getDefaultInstance());
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
        @Nullable TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, item);

        if (parent == null) {
            throw new IllegalStateException("Welcome mat has no parent");
        }

        BlockPos matPos = ctx.getClickedPos();
        parent.registerWelcomeMat(matPos);
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }
}
