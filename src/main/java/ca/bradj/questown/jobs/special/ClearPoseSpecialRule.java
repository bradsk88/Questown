package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import org.jetbrains.annotations.Nullable;

public class ClearPoseSpecialRule extends
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        event.poseClearer().run();
        return null;
    }
}
