package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class WelcomeMatBlock extends TownFlagSubBlock<WelcomeMatBlock.Entity> {
    public static final String ITEM_ID = "welcome_mat_block";
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public WelcomeMatBlock(
    ) {
        super(
                BlockBehaviour.Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
                        .noCollission(),
                Entity::new,
                Entity::tick
        );
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // The drop is handled by Entity.dropWhenOrphaned
        return ImmutableList.of();
    }

    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
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

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        ItemStack item = ctx.getItemInHand();
        @Nullable TownFlagBlockEntity parent = TownFlagBlock.GetParentFromNBT(sl, item);

        BlockPos matPos = ctx.getClickedPos();
        if (parent == null) {
            QT.BLOCK_LOGGER.error("Welcome mat is not associated to an existing flag");
            sl.addFreshEntity(new ItemEntity(sl, matPos.getX(), matPos.getY(), matPos.getZ(), ctx.getItemInHand()));
            return Blocks.AIR.defaultBlockState();
        }

        parent.registerWelcomeMat(matPos);
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }

    @Override
    public BlockState rotate(BlockState p_54125_, Rotation p_54126_) {
        return p_54125_.setValue(FACING, p_54126_.rotate(p_54125_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState p_54122_, Mirror p_54123_) {
        return p_54122_.rotate(p_54123_.getRotation(p_54122_.getValue(FACING)));
    }

    @Override
    protected BlockEntityType<Entity> getTickerEntityType() {
        return TilesInit.WELCOME_MAT.get();
    }

    public static class Entity extends BlockEntity implements TownFlagSubEntity {

        private final List<Runnable> tickListeners = new ArrayList<>();

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.WELCOME_MAT.get(), p_155229_, p_155230_);
        }

        @Override
        public Collection<ItemStack> dropWhenOrphaned(BlockPos flagPos) {
            // TODO: Preserve original input item? (e.g. dark oak pressure plate)
            ItemStack toDrop = Items.OAK_PRESSURE_PLATE.getDefaultInstance();
            TownFlagBlock.StoreParentOnNBT(toDrop, flagPos);
            return ImmutableList.of(toDrop);
        }

        @Override
        public void addTickListener(Runnable listener) {
            this.tickListeners.add(listener);
        }

        public static void tick(
                Level level,
                BlockPos pos,
                BlockState blockState,
                Entity entity
        ) {
            entity.tickListeners.forEach(Runnable::run);
        }
    }
}
