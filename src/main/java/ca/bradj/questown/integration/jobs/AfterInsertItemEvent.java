package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.WorkedSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Function;

public record AfterInsertItemEvent<TOWN>(
        ServerLevel level,
        ItemStack inserted,
        WorkedSpot<BlockPos> workSpot,
        Function<TOWN, TOWN> bopClearer,
        UUID inserter
) {
}
