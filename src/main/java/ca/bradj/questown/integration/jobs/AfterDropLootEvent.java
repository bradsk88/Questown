package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;

public record AfterDropLootEvent(
        QTWorldAccess world,
        BlockPos dropSpot,
        ImmutableList<MCHeldItem> itemsBeforeDrop,
        ImmutableList<MCHeldItem> itemsAfterDrop,
        Consumer<BlockPos> clearStatus
) {
}
