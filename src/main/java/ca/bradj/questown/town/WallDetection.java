package ca.bradj.questown.town;

import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class WallDetection {

    public static WallDetection INSTANCE = new WallDetection();

    public static boolean IsWall(ServerLevel level, Position dp, int y) {
        BlockPos bp = Positions.ToBlock(dp, y);
        BlockPos abp = bp.above();
        if (IsEmpty(level, dp, y)) {
            return false;
        }
        BlockState blockState = level.getBlockState(bp);
        BlockState aboveBlockState = level.getBlockState(abp);
        if (blockState.isAir() || aboveBlockState.isAir()) {
            return false;
        }

        if (blockState.getShape(level, bp).isEmpty() || aboveBlockState.getShape(level, abp).isEmpty()) {
            return false;
        }

        if (isSolid(bp, level, blockState)) {
            if (isSolid(abp, level, aboveBlockState)) {
                return true;
            }
        }

        if (IsDoor(level, dp, y)) {
            return true;
        }

        return false;
    }

    public static boolean IsEmpty(ServerLevel level, Position dp, int y) {
        BlockPos bp = Positions.ToBlock(dp, y);
        BlockPos abp = bp.above();
        boolean empty = level.isEmptyBlock(bp);
        boolean emptyAbove = level.isEmptyBlock(abp);
        return empty || emptyAbove;
    }

    public static boolean IsDoor(ServerLevel level, Position dp, int y) {
        BlockState bs = level.getBlockState(Positions.ToBlock(dp, y));
        return bs.getBlock() instanceof DoorBlock && DoubleBlockHalf.LOWER.equals(bs.getOptionalValue(DoorBlock.HALF).orElse(null));
    }

    public static boolean isSolid(
            BlockPos bp,
            Level level,
            BlockState blockState
    ) {
        if (blockState.getBlock() instanceof AbstractGlassBlock || blockState.getBlock() instanceof IronBarsBlock) {
            return true;
        }
        return blockState.getShape(level, bp).bounds().getSize() >= 1 && !blockState.propagatesSkylightDown(
                level,
                bp
        ) && !blockState.getCollisionShape(level, bp).isEmpty();
    }
}
