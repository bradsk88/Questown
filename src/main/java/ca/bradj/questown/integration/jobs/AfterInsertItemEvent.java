package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.WorkedSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public record AfterInsertItemEvent(
        ServerLevel level,
        ItemStack inserted,
        WorkedSpot<BlockPos> workSpot
) {
}
