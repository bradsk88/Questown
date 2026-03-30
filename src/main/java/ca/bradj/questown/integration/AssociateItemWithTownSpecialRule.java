package ca.bradj.questown.integration;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class AssociateItemWithTownSpecialRule extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <CONTEXT> @Nullable CONTEXT afterExtract(
            CONTEXT ctxInput,
            AfterExtractEvent<CONTEXT> event
    ) {
        CONTEXT town = super.afterExtract(ctxInput, event);
        return event.itemDataApplier().apply(town, TownFlagBlock.getParentData(event.townFlagPos()));
    }
}
