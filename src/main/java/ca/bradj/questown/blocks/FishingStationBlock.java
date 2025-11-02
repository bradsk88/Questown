package ca.bradj.questown.blocks;

import ca.bradj.questown._vanilla.DeployFishingHookRule;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.levelgen.Heightmap;
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

    public static Vec3 getRandomHookPos(
            BlockPos blockPos,
            ServerLevel sl
    ) {
        BlockState blockState = sl.getBlockState(blockPos);
        if (!(blockState.getBlock() instanceof FishingStationBlock)) {
            return null;
        }
        Direction facing = blockState.getValue(FACING).getOpposite();
        BlockPos relative;

        // Start with random
        // TODO: Config
        for (int i = 0; i < 20; i++) {
            relative = blockPos.relative(facing, Compat.nextRandomInt(sl, 5));
            relative = relative.relative(facing.getClockWise(), Compat.nextRandomInt(sl, 5) - 2);
            relative = sl.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, relative);

            // If block below is water and block above is air, we can place the hook here
            BlockState below = sl.getBlockState(relative.below());
            BlockState above = sl.getBlockState(relative);
            if (below.is(Blocks.WATER) && above.isAir()) {
                return Vec3.atBottomCenterOf(relative);
            }
        }
        // If we can't find one randomly, sweep the area in front of the block
        for (int i = 1; i <= 5; i++) {
            relative = blockPos.relative(facing, i);
            for (int j = -2; j <= 2; j++) {
                BlockPos checkPos = relative.relative(facing.getClockWise(), j);
                checkPos = sl.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, checkPos);
                BlockState below = sl.getBlockState(checkPos.below());
                BlockState above = sl.getBlockState(checkPos);
                if (below.is(Blocks.WATER) && above.isAir()) {
                    return Vec3.atBottomCenterOf(checkPos);
                }
            }
        }

        // If we still can't find one, return null
        return null;
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
        if (DeployFishingHookRule.deployHere(sl, p_60505_, p_60506_) != null) {
            return super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        }
        return InteractionResult.CONSUME;
    }

    public static Vec3 getAttachPoint(
            BlockPos p_60505_,
            ServerLevel sl
    ) {
        BlockState bs = sl.getBlockState(p_60505_);
        Direction v = bs.getValue(FACING).getOpposite();
        Vec3 b = Vec3.atBottomCenterOf(p_60505_);
        Vec3 tip = Compat.relative(b.add(0, 1, 0), v, 0.5);
        // Randomly offset left or right
        int r = sl.getRandom().nextInt(3);
        if (r == 0) {
            tip = Compat.relative(tip, v.getClockWise(), 0.1);
        } else if (r == 1) {
            tip = Compat.relative(tip, v.getCounterClockWise(), 0.1);
        }
        return tip;

    }

    @Override
    public String getId() {
        return ITEM_ID;
    }
}
