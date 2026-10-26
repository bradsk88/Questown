package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.jobs.WalkTargets;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * Visitor mob pathfinding. In 1.20.6 the per-cell hook is
 * {@link WalkNodeEvaluator#getPathType(PathfindingContext, int, int, int)} — Questown's routing
 * overrides (campfire hard-block, water-with-roof, fence-gate walkability, fence hard-block) are
 * applied on top of the vanilla result.
 */
@SuppressWarnings("NullableProblems")
public class VillagerPathNavigation extends GroundPathNavigation {
    public VillagerPathNavigation(
            VisitorMobEntity visitorMobEntity,
            Level p21480
    ) {
        super(visitorMobEntity, p21480);
        setCanOpenDoors(true);
        setCanPassDoors(true);
    }

    /**
     * A doorway is a target as it stands. Vanilla lifts any target whose block {@code isSolid()}
     * up to the first non-solid block, and a closed door counts as solid: a chest in a 3x3 store
     * room is used from its doorway, so the villager aimed at the roof above the door, found no
     * path, and stood in the next room facing the wall (r20 #4, probe: walkTarget 150 71 48,
     * pathTarget 150 75 48). A door-opening villager can stand in a door cell, so the lift stops
     * at a door: the target itself, or a door right above a solid target (r21: a target one block
     * low, on the floor under the doorway, walkTarget 150 70 48).
     */
    @Override
    public Path createPath(BlockPos pos, int accuracy) {
        BlockPos door = doorAtOrJustAbove(pos);
        if (door != null) {
            return createPath(ImmutableSet.of(door), 8, false, accuracy);
        }
        BlockPos beside = besideSolidStack(pos);
        if (beside != null) {
            return createPath(ImmutableSet.of(beside), 8, false, accuracy);
        }
        return super.createPath(pos, accuracy);
    }

    /**
     * A target buried under another solid block (a stacked chest, a wall) would be lifted to the
     * top of the stack, usually the roof: the target is fine, the roof never is (dev, r21). Stand
     * beside it instead, at its own level: the first door or standable cell next to it. A single
     * solid block (a bed) keeps vanilla's one-block lift, which sleeping relies on.
     */
    private @Nullable BlockPos besideSolidStack(BlockPos pos) {
        if (!level.getBlockState(pos).isSolid() || !level.getBlockState(pos.above()).isSolid()) {
            return null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (isDoor(n)) {
                return lowerHalf(n);
            }
            if (WalkTargets.isStandable(level, n)) {
                return n;
            }
        }
        return null;
    }

    private @Nullable BlockPos doorAtOrJustAbove(BlockPos pos) {
        if (isDoor(pos)) {
            return lowerHalf(pos);
        }
        if (!level.getBlockState(pos).isSolid()) {
            return null;
        }
        BlockPos above = pos.above();
        return isDoor(above) ? lowerHalf(above) : null;
    }

    private boolean isDoor(BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof DoorBlock;
    }

    private BlockPos lowerHalf(BlockPos doorPos) {
        BlockState state = level.getBlockState(doorPos);
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return doorPos.below();
        }
        return doorPos;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new WalkNodeEvaluator() {
            @Override
            public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                return overridePathType(context, pos, super.getPathType(context, x, y, z));
            }
        };
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private PathType overridePathType(PathfindingContext context, BlockPos pos, PathType type) {
        // The town's campfire is furniture, not a route: vanilla classifies a lit
        // campfire as walkable, so a townie whose path crosses it takes fire damage
        // (CampfireBlock.entityInside deals damage per tick with no armor and no burn
        // time). Hard-block it so no townie path ever routes through a lit campfire.
        BlockState state = context.getBlockState(pos);
        if (CampfireBlock.isLitCampfire(state)) {
            return PathType.BLOCKED;
        }

        FluidState fs = context.level().getFluidState(pos);
        if (!fs.isEmpty()) {
            BlockState above = context.getBlockState(pos.above());
            if (above.isAir()) {
                BlockState roof = context.getBlockState(pos.above()
                                                        .above());
                if (!roof.isAir()) {
                    return PathType.BLOCKED;
                }
            }
        }

        if (state.is(BlockTags.FENCE_GATES)) {
            return PathType.DOOR_WOOD_CLOSED;
        }

        if (state.is(BlockTags.FENCES)) {
            return PathType.BLOCKED;
        }
        if (context.getBlockState(pos.below()).is(BlockTags.FENCES)) {
            return PathType.BLOCKED;
        }

        // A fence gate reading as FENCE is actually openable (the old evaluate hook).
        if (type == PathType.FENCE && state.getBlock() instanceof FenceGateBlock) {
            return PathType.DOOR_OPEN;
        }

        return type;
    }
}
