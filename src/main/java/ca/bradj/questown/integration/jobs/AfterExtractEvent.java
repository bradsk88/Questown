package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiFunction;

public record AfterExtractEvent<CONTEXT>(
        QTWorldAccess world,
        BlockPos workSpot,
        BlockPos townFlagPos,
        BiFunction<CONTEXT, ImmutableMap<String, Integer>, CONTEXT> itemDataApplier,
        BiFunction<CONTEXT, Float, CONTEXT> hungerUpdater,
        MoodApplier<CONTEXT> moodUpdater
) {
    /**
     * Applies a mood effect to the working villager for {@code durationTicks},
     * threading the town context (live handle in realtime, immutable state in warp).
     */
    @FunctionalInterface
    public interface MoodApplier<CONTEXT> {
        CONTEXT apply(CONTEXT ctx, ResourceLocation effect, long durationTicks);
    }
}
