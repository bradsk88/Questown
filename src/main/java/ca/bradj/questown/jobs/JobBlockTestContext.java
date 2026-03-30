package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.function.Supplier;

public record JobBlockTestContext(
        QTWorldAccess world,
        WorkLocation.BlockInfo blockInfo,
        BlockPos blockPos,
        Supplier<Collection<MCHeldItem>> heldItems,
        Supplier<? extends Collection<Item>> townUniqueItems,
        boolean jobBlockAlreadyUsed,
        boolean jobActive
) {
    public JobBlockTestContext withPos(BlockPos p) {
        return new JobBlockTestContext(world, blockInfo, p, heldItems, townUniqueItems, jobBlockAlreadyUsed, jobActive);
    }

    public JobBlockTestContext withBlockAlreadyUsed(boolean b) {
        return new JobBlockTestContext(
                world,
                blockInfo,
                blockPos,
                heldItems,
                townUniqueItems,
                b,
                jobActive
        );
    }
    public JobBlockTestContext withJobAlreadyStarted(boolean b) {
        return new JobBlockTestContext(
                world,
                blockInfo,
                blockPos,
                heldItems,
                townUniqueItems,
                jobBlockAlreadyUsed,
                b
        );
    }
}
