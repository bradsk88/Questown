package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import org.jetbrains.annotations.Nullable;

public class FillHungerSpecialRule extends
        JobPhaseModifier {

    private final float percent;

    public FillHungerSpecialRule(float percent) {
        this.percent = percent;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        return event.hungerUpdater().apply(ctxInput, percent);
    }
}
