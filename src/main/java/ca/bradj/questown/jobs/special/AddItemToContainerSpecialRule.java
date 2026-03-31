package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class AddItemToContainerSpecialRule extends
        JobPhaseModifier implements QTNativeRule {

    public AddItemToContainerSpecialRule() {

    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        BlockPos ws = event.workSpot().workPosition();
        if (!event.world().insertIntoContainer(ws, event.inserted())) {
            QT.JOB_LOGGER.error("Item lost due to not enough space in target container @ {}: {}", ws, event.inserted());
        }
        return ctxOut;
    }
}
