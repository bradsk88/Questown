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
        // The item just extracted, or null when there was none.
        @Nullable MCHeldItem extractedItem,
        // Records a found item as something the town knows.
        BiFunction<CONTEXT, MCHeldItem, CONTEXT> knowledgeUpdater
) {
    /** Gives the working villager a mood effect for {@code durationTicks}. */
    @FunctionalInterface
    public interface MoodApplier<CONTEXT> {
        CONTEXT apply(CONTEXT ctx, ResourceLocation effect, long durationTicks);
    }
}
