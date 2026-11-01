package ca.bradj.questown.blocks;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.MapC;

import ca.bradj.questown.items.QTNBT;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public class BlockOfProgress extends Block {
    public static final String ITEM_ID = "block_of_progress";

    protected static final VoxelShape SHAPE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    public BlockOfProgress(
    ) {
        super(Compat.P.of().mapColor(MapC.SNOW).strength(1.0F, 10.0F).noOcclusion().impl());
    }

    @Override
    public VoxelShape getShape(
            BlockState p_60555_,
            BlockGetter p_60556_,
            BlockPos p_60557_,
            CollisionContext p_60558_
    ) {
        return SHAPE;
    }

    /**
     * Records which root job earned this Block of Progress (the flag's BOP tab says "Earned by").
     * It guarantees nothing: a job change with a BOP takes an open job-change quest's job, else a
     * random root.
     */
    public static void markEarnedBy(ItemStack item, JobID job) {
        Compat.updateItemDataTag(item, tag -> QTNBT.put(tag, EARNED_BY, JobID.toTag(job)));
    }

    /** The root job that earned this BOP, or empty. Reads an older "gold" BOP's job too. */
    public static Optional<JobID> getEarnedBy(ItemStack item) {
        CompoundTag data = customData(item);
        if (QTNBT.contains(data, EARNED_BY)) {
            return Optional.of(JobID.fromTag(QTNBT.getCompound(data, EARNED_BY)));
        }
        if (QTNBT.contains(data, LEGACY_GUARANTEED_JOB)) {
            return Optional.of(JobID.fromTag(QTNBT.getCompound(data, LEGACY_GUARANTEED_JOB)));
        }
        return Optional.empty();
    }

    private static CompoundTag customData(ItemStack stack) {
        return Compat.itemDataTag(stack);
    }

    private static final String EARNED_BY = "earned_by";
    private static final String LEGACY_GUARANTEED_JOB = "guaranteed_job";
}
