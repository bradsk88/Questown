package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

public record AfterExtractEvent<CONTEXT>(
        QTWorldAccess world,
        BlockPos workSpot,
        BlockPos townFlagPos,
        BiFunction<CONTEXT, ImmutableMap<String, Integer>, CONTEXT> itemDataApplier,
        BiFunction<CONTEXT, Float, CONTEXT> hungerUpdater,
        MoodApplier<CONTEXT> moodUpdater,
        // The product just extracted (null when extraction produced no item). Lets a
        // rule react to what was produced — e.g. scouting reads the gatherer map's biome.
        @Nullable MCHeldItem extractedItem,
        // Registers a found-loot identity as town knowledge (carries biome + tool prefix).
        BiFunction<CONTEXT, MCHeldItem, CONTEXT> knowledgeUpdater
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
