package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import org.jetbrains.annotations.Nullable;

public class ClearPoseSpecialRule implements
        JobPhaseModifier {
    @Override
    public <X> @Nullable X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        event.poseClearer().run();
        return null;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        return null;
    }

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        return null;
    }
}
