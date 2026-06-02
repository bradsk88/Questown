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
 * A writing {@link VoidLevel} sibling that backs real worldgen ({@code TreeFeature.place})
 * with an in-memory snapshot during time warp.
 * <p>
 * Reads route snapshot-first (falling back to the real level for everything not yet
 * overridden); writes route into the snapshot instead of the live world. A {@code dryRun}
 * mode discards writes (like {@link VoidLevel}) so the same feature call can be used as a
 * plantability probe. This is the decoupling doc's reference example for complex world
 * interaction under warp (see ADR-0005).
 */
public class SnapshotWorldGenLevel extends VoidLevel {

    /** Snapshot/dirty-set access, implemented by {@code WarpWorldAccess}. */
    public interface SnapshotAccess {
        /** Snapshot-first block state; never null (falls back to the real level / air). */
        BlockState getBlock(BlockPos pos);

        /** Writes a block into the snapshot and marks it dirty for {@code applyTo}. */
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
