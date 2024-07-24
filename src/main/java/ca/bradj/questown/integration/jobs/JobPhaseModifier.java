package ca.bradj.questown.integration.jobs;

import org.jetbrains.annotations.Nullable;

public interface JobPhaseModifier {

    JobPhaseModifier NO_OP = new JobPhaseModifier() {
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
        public Void beforeStateChange(
                BeforeStateChangeEvent event
        ) {
            return null;
        }
    };

    <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    );

    <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    );

    Void beforeStateChange(
            BeforeStateChangeEvent event
    );
}
