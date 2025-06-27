package ca.bradj.questown.integration;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Util;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class TakeFromSlotSpecialRule extends JobPhaseModifier {
    private final int slotIndex;

    public TakeFromSlotSpecialRule(int i) {
        super();
        this.slotIndex = i;
    }

    @Override
    public <CONTEXT> @Nullable CONTEXT beforeExtract(
            CONTEXT ctxInput,
            BeforeExtractEvent<CONTEXT> event
    ) {
        BlockEntity entity = event.level().getBlockEntity(event.workSpot());
        CONTEXT ctxBefore = super.beforeExtract(ctxInput, event);
        if (!(entity instanceof Container c)) {
            QT.BLOCK_LOGGER.error(
                    "{}: BlockEntity at {} is not a Container, cannot apply special rule.",
                    getClass(),
                    Util.getTinyString(event.workSpot())
            );
            return ctxBefore;
        }
        ItemStack i = c.removeItem(slotIndex, 1);
        return event.entity().tryGiveItem(ctxBefore, MCHeldItem.fromTown(i), InventoryFullStrategy.DROP_ON_GROUND);
    }
}
