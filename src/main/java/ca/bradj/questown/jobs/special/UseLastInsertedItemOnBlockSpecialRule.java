package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class UseLastInsertedItemOnBlockSpecialRule extends
        JobPhaseModifier implements QTNativeRule {
    @Override
    public <X> X beforeExtract(
            X context,
            BeforeExtractEvent<X> event
    ) {
        Item item = event.lastInsertedItem();
        if (item == null) {
            return null;
        }
        BlockPos groundPos = event.workSpot();
        QTWorldAccess world = event.world();
        boolean success = world.useItemOnBlock(item.getDefaultInstance(), groundPos);
        if (!success) {
            QT.JOB_LOGGER.error("Failed to use item {} on block at {}", item, groundPos);
        }
        return null;
    }
}
