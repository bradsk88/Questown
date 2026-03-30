package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.function.Supplier;

public record BeforeExtractEvent<TOWN>(
        QTWorldAccess world,
        ItemAcceptor<TOWN> entity,
        BlockPos workSpot,
        Item lastInsertedItem,
        Runnable poseClearer,
        Supplier<Collection<BlockPos>> jobBlockPositions
) {
}
