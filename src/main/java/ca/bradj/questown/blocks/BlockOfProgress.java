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

    /** Marks the stack as a plain (fungible) Block of Progress. */
    public static void bless(ItemStack item) {
        Compat.updateItemDataTag(item, tag -> QTNBT.putBoolean(tag, BLESSED, true));
    }

    /**
     * Marks the stack as a "gold" (blessed) Block of Progress: spending it on a root job change
     * is guaranteed to take {@code job} instead of picking a random root. Depositing villagers
     * bless their BOPs with the root job that earned them (see TownFlagBlockEntity).
     */
    public static void bless(ItemStack item, JobID job) {
        Compat.updateItemDataTag(item, tag -> {
            QTNBT.putBoolean(tag, BLESSED, true);
            QTNBT.put(tag, GUARANTEED_JOB, JobID.toTag(job));
        });
    }

    public static boolean isBlessed(ItemStack item) {
        return QTNBT.getBoolean(customData(item), BLESSED, false);
    }

    /** The job a blessed BOP guarantees, or empty for a plain BOP. */
    public static Optional<JobID> getBlessedJob(ItemStack item) {
        if (!isBlessed(item) || !QTNBT.contains(customData(item), GUARANTEED_JOB)) {
            return Optional.empty();
        }
        return Optional.of(JobID.fromTag(QTNBT.getCompound(customData(item), GUARANTEED_JOB)));
    }

    private static CompoundTag customData(ItemStack stack) {
        return Compat.itemDataTag(stack);
    }

    private static final String BLESSED = "blessed";
    private static final String GUARANTEED_JOB = "guaranteed_job";
}
