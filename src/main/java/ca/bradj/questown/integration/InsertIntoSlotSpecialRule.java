package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.mc.Util;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class InsertIntoSlotSpecialRule extends JobPhaseModifier {
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
        BlockEntity entity = event.level().getBlockEntity(event.workSpot().workPosition());
        if (!(entity instanceof Container c)) {
            QT.BLOCK_LOGGER.error(
                    "{}: BlockEntity at {} is not a Container, cannot apply special rule.",
                    getClass(),
                    Util.getTinyString(event.workSpot().workPosition())
            );
            return context;
        }

        // TODO[WARP]: Ensure world containers get filled/emptied after time warp
        c.setItem(slotIndex, event.inserted());

        return context;
    }
}
