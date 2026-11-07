package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.jobs.WalkTargets;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/** Villager pathfinding: vanilla's, but lit campfires and fences are blocked, fence gates are walkable, and water under a roof is allowed. */
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

    /** A target under a stack of blocks: stand beside it, at its level, rather than on top. A single block keeps vanilla's lift. */
    private @Nullable BlockPos besideSolidStack(BlockPos pos) {
        if (!ca.bradj.questown.mc.Compat.isSolid(level.getBlockState(pos)) || !ca.bradj.questown.mc.Compat.isSolid(level.getBlockState(pos.above()))) {
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
        if (!ca.bradj.questown.mc.Compat.isSolid(level.getBlockState(pos))) {
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
            public BlockPathTypes getBlockPathType(BlockGetter context, int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                return overridePathType(context, pos, super.getBlockPathType(context, x, y, z));
            }
        };
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private BlockPathTypes overridePathType(BlockGetter context, BlockPos pos, BlockPathTypes type) {
        // A lit campfire burns villagers that walk through it, so block it.
        BlockState state = context.getBlockState(pos);
        if (CampfireBlock.isLitCampfire(state)) {
            return BlockPathTypes.BLOCKED;
        }

        FluidState fs = context.getFluidState(pos);
        if (!fs.isEmpty()) {
            BlockState above = context.getBlockState(pos.above());
            if (above.isAir()) {
                BlockState roof = context.getBlockState(pos.above()
                                                        .above());
                if (!roof.isAir()) {
                    return BlockPathTypes.BLOCKED;
                }
            }
        }

        if (state.is(BlockTags.FENCE_GATES)) {
            return BlockPathTypes.DOOR_WOOD_CLOSED;
        }

        if (state.is(BlockTags.FENCES)) {
            return BlockPathTypes.BLOCKED;
        }
        if (context.getBlockState(pos.below()).is(BlockTags.FENCES)) {
            return BlockPathTypes.BLOCKED;
        }

        // A fence gate can be opened.
        if (type == BlockPathTypes.FENCE && state.getBlock() instanceof FenceGateBlock) {
            return BlockPathTypes.DOOR_OPEN;
        }

        return type;
    }
}
