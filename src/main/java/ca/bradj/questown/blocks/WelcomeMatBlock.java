package ca.bradj.questown.blocks;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.MapC;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class WelcomeMatBlock extends TownFlagSubBlock<WelcomeMatBlock.Entity> {
    public static final MapCodec<WelcomeMatBlock> CODEC = MapCodec.unit(WelcomeMatBlock::new);

    @Override
    public MapCodec<WelcomeMatBlock> codec() { return CODEC; }

    public static final String ITEM_ID = "welcome_mat_block";
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public WelcomeMatBlock(
    ) {
        super(
                Compat.P
                        .of().mapColor(MapC.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
                        .noCollission().impl(),
                Entity::new,
                Entity::tick
        );
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootParams.Builder p_60538_
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
    public BlockState rotate(
            BlockState p_54125_,
            Rotation p_54126_
    ) {
        return p_54125_.setValue(FACING, p_54126_.rotate(p_54125_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(
            BlockState p_54122_,
            Mirror p_54123_
    ) {
        return p_54122_.rotate(p_54123_.getRotation(p_54122_.getValue(FACING)));
    }

    @Override
    protected BlockEntityType<Entity> getTickerEntityType() {
        return TilesInit.WELCOME_MAT.get();
    }

    public static class Entity extends BlockEntity implements TownFlagSubEntity {

        private final List<Runnable> tickListeners = new ArrayList<>();

        // c3 ("hard to use wrong"): a welcome mat must belong to a live flag (registered
        // in that flag's POIs). Enforced post-load so the place->register ordering is fine
        // (placeWelcomeMat registers synchronously before the first tick), and so a freshly
        // reloaded world's flag POIs have settled before we judge ownership. Reported once.
        private int c3Ticks = 0;
        private boolean c3Reported = false;
        private static final int C3_GRACE_TICKS = 120; // 6s

        /** true when some live flag has registered this mat position in its POIs. */
        private static boolean ownedByFlag(BlockPos pos) {
            for (TownFlagBlockEntity f : TownFlags.all()) {
                if (f.getWelcomeMats().contains(pos)) {
                    return true;
                }
            }
            return false;
        }

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.WELCOME_MAT.get(), p_155229_, p_155230_);
        }

        @Override
        public Block getBlock() {
            return this.getBlockState().getBlock();
        }

        @Override
        public void runWhenOrphaned(
                ServerLevel sl,
                Block childBlock,
                BlockPos childPos,
                BlockPos flagPos
        ) {
            ItemStack toDrop = Items.OAK_PRESSURE_PLATE.getDefaultInstance();
            TownFlagBlock.StoreParentOnNBT(toDrop, flagPos);
            TownFlagSubBlocks.dropDrops(sl, childPos, ImmutableList.of(toDrop));
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
            if (level instanceof ServerLevel sl) {
                if (entity.c3Ticks < C3_GRACE_TICKS) {
                    entity.c3Ticks++;
                } else if (!entity.c3Reported) {
                    entity.c3Reported = true;
                    if (ownedByFlag(pos)) {
                        QT.BLOCK_LOGGER.info("[c3] welcome mat @{} is registered to a flag - OK", pos);
                    } else {
                        QT.BLOCK_LOGGER.warn("[c3] welcome mat @{} has NO owning flag (use-wrong placement) - reverting to air", pos);
                        sl.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        TownFlagSubBlocks.dropDrops(sl, pos,
                                ImmutableList.of(Items.OAK_PRESSURE_PLATE.getDefaultInstance()));
                        return;
                    }
                }
            }
            entity.tickListeners.forEach(Runnable::run);
        }
    }
}
