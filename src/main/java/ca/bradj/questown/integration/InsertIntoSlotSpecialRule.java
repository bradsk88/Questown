package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.mc.Util;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class InsertIntoSlotSpecialRule extends JobPhaseModifier implements QTNativeRule {
    private final int slotIndex;

    public InsertIntoSlotSpecialRule(int i) {
        super();
        this.slotIndex = i;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT context = super.afterInsertItem(ctxInput, event);
        BlockPos pos = event.workSpot().workPosition();
        if (!event.world().insertIntoSlot(pos, slotIndex, event.inserted())) {
            QT.BLOCK_LOGGER.error(
                    "{}: No container at {}, cannot apply special rule.",
                    getClass(),
                    Util.getTinyString(pos)
            );
        }
        return context;
    }
}
