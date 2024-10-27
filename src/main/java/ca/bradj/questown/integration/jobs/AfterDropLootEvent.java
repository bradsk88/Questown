package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

public record AfterDropLootEvent(
        ServerLevel level,
        BlockPos dropSpot,
        ImmutableList<MCHeldItem> itemsBeforeDrop,
        ImmutableList<MCHeldItem> itemsAfterDrop,
        Consumer<BlockPos> clearStatus
) {
}
