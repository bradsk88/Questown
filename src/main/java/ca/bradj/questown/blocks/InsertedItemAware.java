package ca.bradj.questown.blocks;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import net.minecraft.core.BlockPos;

public interface InsertedItemAware {
    void handleInsertedItem(MCExtra extra, BlockPos bp, MCHeldItem item);
}
