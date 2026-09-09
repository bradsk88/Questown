package ca.bradj.questown.town;

import ca.bradj.questown.blocks.FalseDoorBlock;
import ca.bradj.questown.blocks.FalseWallBlock;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class WallDetection {

    public static WallDetection INSTANCE = new WallDetection();

    public static boolean IsWall(ServerLevel level, Position dp, int y) {
        BlockPos bp = Positions.ToBlock(dp, y);
        BlockPos abp = bp.above();
        BlockState blockState = level.getBlockState(bp);
        if (blockState.getBlock() instanceof FalseWallBlock || blockState.getBlock() instanceof FalseDoorBlock) {
            return true;
        }

        if (IsEmpty(level, dp, y)) {
            return false;
        }

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

    /**
     * True when a two-block-tall villager can stand at {@code dp}: the feet block (at
     * {@code y}) and the head block (at {@code y + 1}) are both passable (air or no
     * collision shape). This is the interior counterpart of {@link #IsWall}: a wall is
     * solid at both levels, but a position that is merely empty at {@code y} OR
     * {@code y + 1} is not necessarily standable — a one-block-tall gap (air at feet,
     * solid roof at head) reads as "not a wall" yet cannot be walked into. Room
     * detection uses this to reject rooms whose interior a villager cannot enter.
     */
    public static boolean IsWalkable(ServerLevel level, Position dp, int y) {
        return isPassable(level, Positions.ToBlock(dp, y)) && isPassable(
                level,
                Positions.ToBlock(dp, y).above()
        );
    }

    private static boolean isPassable(ServerLevel level, BlockPos bp) {
        BlockState bs = level.getBlockState(bp);
        return bs.isAir() || bs.getCollisionShape(level, bp).isEmpty();
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
        if (bs.getBlock() instanceof FalseDoorBlock) {
            return true;
        }
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
        if (blockState.getBlock() instanceof StairBlock) {
            return false;
        }
        return blockState.getShape(level, bp).bounds().getSize() >= 1 && !blockState.propagatesSkylightDown(
                level,
                bp
        ) && !blockState.getCollisionShape(level, bp).isEmpty();
    }
}
