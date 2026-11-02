package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Gives the working villager a mood effect when a product is extracted (the eating moods). */
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
