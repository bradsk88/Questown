package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.JobBlockTestContext;
import org.jetbrains.annotations.Nullable;

public abstract class JobPhaseModifier {

    @SuppressWarnings("RedundantMethodOverride")
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
                AfterInsertItemEvent<CONTEXT> event
        ) {
            return null;
        }

        @Override
        public <CONTEXT> @Nullable CONTEXT afterDropLoot(
                CONTEXT ctxInput,
                AfterDropLootEvent event
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

    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        return null;
    }

    public <CONTEXT> @Nullable CONTEXT afterDropLoot(
            CONTEXT ctxInput,
            AfterDropLootEvent event
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

    public boolean postJobBlockCheckPassed(
            JobBlockTestContext ctx
    ) {
        return true;
    }
}
