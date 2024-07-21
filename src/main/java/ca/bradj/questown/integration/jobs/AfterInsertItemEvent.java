package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.WorkSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public record AfterInsertItemEvent(
        ServerLevel level,
        ItemStack inserted,
        WorkSpot<Integer, BlockPos> workSpot
) {
}
