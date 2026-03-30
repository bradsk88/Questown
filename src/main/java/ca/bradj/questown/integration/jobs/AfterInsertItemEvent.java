package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Function;

public record AfterInsertItemEvent<TOWN>(
        QTWorldAccess world,
        ItemStack inserted,
        WorkedSpot<BlockPos> workSpot,
        Function<TOWN, TOWN> bopClearer,
        UUID inserter
) {
}
