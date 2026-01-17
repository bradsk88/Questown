package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.mc.Util;
import net.minecraft.core.BlockPos;
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
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        // ctxOut is null if parent returns null (the default), use ctxInput in that case
        CONTEXT context = ctxOut != null ? ctxOut : ctxInput;
        BlockPos workPos = event.workSpot().workPosition();
        BlockEntity entity = event.level().getBlockEntity(workPos);

        if (entity == null) {
            // During warp, cooking is handled by EagerCookResolver at the start of warp.
            // This rule is a no-op during warp - just return context unchanged.
            QT.BLOCK_LOGGER.debug(
                    "{}: BlockEntity at {} is null (during warp), cooking handled by EagerCookResolver.",
                    getClass(),
                    Util.getTinyString(workPos)
            );
            return context;
        }
        if (!(entity instanceof Container c)) {
            QT.BLOCK_LOGGER.error(
                    "{}: BlockEntity at {} is not a Container, cannot apply special rule.",
                    getClass(),
                    Util.getTinyString(workPos)
            );
            return context;
        }

        c.setItem(slotIndex, event.inserted());

        return context;
    }
}
