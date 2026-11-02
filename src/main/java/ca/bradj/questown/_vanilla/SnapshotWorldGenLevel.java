package ca.bradj.questown._vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A world view for growing trees during a warp: reads fall back to the real level, writes stay in
 * memory. With {@code dryRun}, writes are dropped.
 */
public class SnapshotWorldGenLevel extends VoidLevel {

    public interface SnapshotAccess {
        BlockState getBlock(BlockPos pos);

        void setBlock(BlockPos pos, BlockState state);
    }

    private final ServerLevel delegate;
    private final SnapshotAccess snapshot;
    private final boolean dryRun;

    public SnapshotWorldGenLevel(ServerLevel delegate, SnapshotAccess snapshot, boolean dryRun) {
        super(delegate);
        this.delegate = delegate;
        this.snapshot = snapshot;
        this.dryRun = dryRun;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return snapshot.getBlock(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return snapshot.getBlock(pos).getFluidState();
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (!dryRun) {
            snapshot.setBlock(pos.immutable(), state);
        }
        return true;
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        if (!dryRun) {
            snapshot.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState());
        }
        return true;
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        if (!dryRun) {
            snapshot.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState());
        }
        return true;
    }
}
