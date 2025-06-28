package ca.bradj.questown.integration.jobs;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiFunction;

public record AfterExtractEvent<CONTEXT>(
        ServerLevel level,
        BlockPos workSpot,
        BlockPos townFlagPos,
        BiFunction<CONTEXT, ImmutableMap<String, Integer>, CONTEXT> itemDataApplier
) {
}
