package ca.bradj.questown.blocks;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.MapC;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class JobBoardBlock extends RoomBlock {
    public static final MapCodec<JobBoardBlock> CODEC = MapCodec.unit(JobBoardBlock::new);

    @Override
    public MapCodec<JobBoardBlock> codec() { return CODEC; }

    public static final String ITEM_ID = "job_board_block";
    private final ArrayList<OpenMenuListener> openMenuListeners = new ArrayList<>();

    public JobBoardBlock(
    ) {
        super(
                Compat.P
                        .of().mapColor(MapC.COLOR_BROWN)
                        .strength(1.0F, 10.0F).
                        noOcclusion().impl()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootParams.Builder p_60538_
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
            TownFlagBlock.tellPlacerNotLinked(ctx, "message.job_board.not_linked");
            sl.addFreshEntity(new ItemEntity(sl, matPos.getX(), matPos.getY(), matPos.getZ(), ctx.getItemInHand()));
            return Blocks.AIR.defaultBlockState();
        }

        parent.registerJobsBoard(matPos);
        BlockState superState = super.getStateForPlacement(ctx); // registers the block-room on the flag
        if (superState == null) {
            return null;
        }
        return superState.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock())) {
            // Actually removed (not replaced by another block state): drop the
            // flag's persisted board registration so a broken board does not
            // leave a stale entry that errors on the next world load.
            for (TownFlagBlockEntity flag : TownFlags.all()) {
                flag.removeJobsBoard(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public void addOpenMenuListener(OpenMenuListener l) {
        this.openMenuListeners.add(l);
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos p_60505_,
            Player p_60506_,
            BlockHitResult p_60508_
    ) {
        boolean clientSide = p_60504_.isClientSide();
        if (!clientSide) {
            for (OpenMenuListener l : openMenuListeners) { // TODO: make listeners survive a reboot
                l.openMenuRequested((ServerPlayer) p_60506_, false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public String getId() {
        return ITEM_ID;
    }
}
