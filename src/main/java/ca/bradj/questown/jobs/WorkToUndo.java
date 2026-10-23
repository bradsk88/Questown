package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The items a townie inserted into one job block during its current work cycle, so the cycle can
 * be undone (the townie grabs them back) when the work is abandoned. Cleared when the job completes.
 */
public record WorkToUndo(
        JobID jobID,
        BlockPos pos,
        ImmutableList<MCHeldItem> itemsInserted
) {
    public static WorkToUndo and(
            @Nullable WorkToUndo workToUndo,
            JobID id,
            BlockPos bp,
            MCHeldItem item
    ) {
        if (workToUndo == null) {
            return new WorkToUndo(id, bp, ImmutableList.of(item));
        }
        return new WorkToUndo(
                id, bp,
                ImmutableList.<MCHeldItem>builder().addAll(workToUndo.itemsInserted()).add(item).build()
        );
    }
}
