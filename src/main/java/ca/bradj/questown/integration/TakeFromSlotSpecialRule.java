package ca.bradj.questown.integration;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class TakeFromSlotSpecialRule extends JobPhaseModifier implements QTNativeRule {
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
        CONTEXT ctxBefore = super.beforeExtract(ctxInput, event);
        if (ctxBefore == null) {
            ctxBefore = ctxInput;
        }
        ItemStack extracted = event.world().extractFromSlot(event.workSpot(), slotIndex, 1);
        if (extracted.isEmpty()) {
            QT.BLOCK_LOGGER.error(
                    "{}: No container or empty slot at {}, cannot apply special rule.",
                    getClass(),
                    Util.getTinyString(event.workSpot())
            );
            return ctxBefore;
        }
        return event.entity().tryGiveItem(ctxBefore, MCHeldItem.fromTown(extracted), InventoryFullStrategy.DROP_ON_GROUND);
    }
}
