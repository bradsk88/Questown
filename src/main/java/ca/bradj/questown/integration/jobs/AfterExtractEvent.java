package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;

import java.util.function.BiFunction;

public record AfterExtractEvent<CONTEXT>(
        QTWorldAccess world,
        BlockPos workSpot,
        BlockPos townFlagPos,
        BiFunction<CONTEXT, ImmutableMap<String, Integer>, CONTEXT> itemDataApplier,
        BiFunction<CONTEXT, Float, CONTEXT> hungerUpdater
) {
}
