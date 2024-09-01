package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;

public class FillHungerSpecialRule extends
        JobPhaseModifier {

    private final float percent;

    public FillHungerSpecialRule(float percent) {
        this.percent = percent;
    }

    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        return event.hungerUpdater().apply(context, percent);
    }
}
