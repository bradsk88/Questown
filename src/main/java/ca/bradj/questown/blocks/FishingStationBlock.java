package ca.bradj.questown.blocks;

import ca.bradj.questown._vanilla.entities.EntitiesInit;
import ca.bradj.questown._vanilla.entities.FishingHook;
import ca.bradj.questown.core.init.TilesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class FishingStationBlock extends RoomBlock {
    public static final String ITEM_ID = "fishing_station";

    public FishingStationBlock(
    ) {
        super(Properties.of(Material.WOOD, MaterialColor.WOOD).strength(1.0F, 10.0F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        @Nullable BlockState blockState = super.getStateForPlacement(ctx);
        blockState = blockState.setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        return blockState;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
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
        if (p_60507_ != InteractionHand.MAIN_HAND) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        if (!(p_60504_ instanceof ServerLevel sl)) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        FishingHook e = EntitiesInit.FISHIN_HOOK.get().create(sl);
        if (e == null) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        e.setOwner(p_60506_, getAttachPoint(p_60505_, sl));
        Vec3 push = Vec3.atCenterOf(p_60505_);
        push = push.subtract(Vec3.atCenterOf(p_60506_.blockPosition()));
        push = push.normalize();
        push = push.multiply(3, 1, 3);
        e.setPos(Vec3.atBottomCenterOf(p_60505_.offset(push.x, 0, push.z)));
        sl.addFreshEntity(e);
        return InteractionResult.CONSUME;
    }

    private Vec3 getAttachPoint(
            BlockPos p_60505_,
            ServerLevel sl
    ) {
        BlockState bs = sl.getBlockState(p_60505_);
        Direction v = bs.getValue(FACING).getOpposite();
        Vec3 b = Vec3.atBottomCenterOf(p_60505_);
        Vec3 tip = b.add(0, 1, 0).relative(v, 0.5);
        // Randomly offset left or right
        int r = sl.getRandom().nextInt(3);
        if (r == 0) {
            tip = tip.relative(v.getClockWise(), 0.1);
        } else if (r == 1) {
            tip = tip.relative(v.getCounterClockWise(), 0.1);
        }
        return tip;

    }

    @Override
    public String getId() {
        return ITEM_ID;
    }
}
