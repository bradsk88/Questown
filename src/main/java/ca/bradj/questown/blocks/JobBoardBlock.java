package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class JobBoardBlock extends TownFlagSubBlock<JobBoardBlock.Entity> {
    public static final String ITEM_ID = "job_board_block";
    private final ArrayList<OpenMenuListener> openMenuListeners = new ArrayList<>();

    public JobBoardBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F).
                        noOcclusion(),
                Entity::new,
                Entity::tick
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // The drop is handled by Entity.dropWhenOrphaned
        return ImmutableList.of();
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
            QT.BLOCK_LOGGER.error("Job Board is not associated to an existing flag");
            sl.addFreshEntity(new ItemEntity(sl, matPos.getX(), matPos.getY(), matPos.getZ(), ctx.getItemInHand()));
            return Blocks.AIR.defaultBlockState();
        }

        parent.registerJobsBoard(matPos);
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }

    public void addOpenMenuListener(OpenMenuListener l) {
        this.openMenuListeners.add(l);
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        boolean informed = false;
        boolean clientSide = p_60504_.isClientSide();
        if (!clientSide) {
            for (OpenMenuListener l : openMenuListeners) { // TODO: make listeners survive a reboot
                l.openMenuRequested((ServerPlayer) p_60506_);
                informed = true;
            }
        }
        if (informed) {
            return InteractionResult.sidedSuccess(clientSide);
        }
        return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
    }

    @Override
    protected BlockEntityType<Entity> getTickerEntityType() {
        return TilesInit.JOB_BOARD.get();
    }

    public static class Entity extends BlockEntity implements TownFlagSubEntity {

        private final List<Runnable> tickListeners = new ArrayList<>();

        public Entity(
                BlockPos p_155229_,
                BlockState p_155230_
        ) {
            super(TilesInit.JOB_BOARD.get(), p_155229_, p_155230_);
        }

        @Override
        public Collection<ItemStack> dropWhenOrphaned(BlockPos flagPos) {
            ItemStack toDrop = Items.OAK_SIGN.getDefaultInstance();
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
