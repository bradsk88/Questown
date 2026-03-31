package ca.bradj.questown.jobs.special;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class ClearBOPSpecialRule extends
        JobPhaseModifier implements QTNativeRule {

    public ClearBOPSpecialRule() {

    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        if (event.inserted().is(ItemsInit.BLOCK_OF_PROGRESS.get())) {
            return event.bopClearer().apply(ctxOut);
        }
        return ctxOut;
    }
}
