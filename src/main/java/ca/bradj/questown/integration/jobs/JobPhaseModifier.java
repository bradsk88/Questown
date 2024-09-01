package ca.bradj.questown.integration.jobs;

import org.jetbrains.annotations.Nullable;

public class JobPhaseModifier {

    public static JobPhaseModifier NO_OP = new JobPhaseModifier() {
        @Override
        public <X> @Nullable X beforeExtract(
                X input,
                BeforeExtractEvent<X> event
        ) {
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
        public Void beforeMoveToNextState(
                BeforeMoveToNextStateEvent event
        ) {
            return null;
        }

        @Override
        public void beforeTick(BeforeTickEvent bxEvent) {

        }
    };

    // Return null if nothing happens.
    // Return either a modified input (via functions available on event) or the
    // original input if something happened.
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        return null;
    }

    // TOOD: Potentially phase out. Was used for farmer_till but changed that to
    // run beforeExtract for better state management.
    public Void beforeMoveToNextState(
            BeforeMoveToNextStateEvent event
    ) {
        return null;
    }

    public void beforeTick(BeforeTickEvent bxEvent) {
    }

    public void beforeInit(BeforeInitEvent bxEvent) {

    }
}
