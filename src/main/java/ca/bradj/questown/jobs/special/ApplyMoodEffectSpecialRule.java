package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Applies a mood effect to the working villager when a product is extracted —
 * e.g. the comfortable/uncomfortable eating moods. Replaces the former
 * {@code EffectMetaItem} result that the eating jobs used to emit (see ADR-0003).
 * Duration is read lazily from config at apply time.
 */
public class ApplyMoodEffectSpecialRule extends
        JobPhaseModifier implements QTNativeRule {

    private final ResourceLocation effect;
    private final Supplier<Long> durationTicks;

    public ApplyMoodEffectSpecialRule(
            ResourceLocation effect,
            Supplier<Long> durationTicks
    ) {
        this.effect = effect;
        this.durationTicks = durationTicks;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        return event.moodUpdater().apply(ctxInput, effect, durationTicks.get());
    }
}
