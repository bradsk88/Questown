package ca.bradj.questown.blocks;

import ca.bradj.questown.jobs.declarative.MCExtra;
import net.minecraft.core.BlockPos;

public interface ExtractedItemAware {
    void handleExtractedItem(MCExtra extra, BlockPos bp);
}
