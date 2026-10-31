package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.QTNativeRule;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The inserted item goes on the display at the work spot, in the slot it fits (a display job,
 * dev 2026-10-28: the villager puts armor on a stand, or a weapon in a frame).
 */
public class DressDisplaySpecialRule extends JobPhaseModifier implements QTNativeRule {

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        BlockPos ws = event.workSpot().workPosition();
        ItemStack item = event.inserted();
        List<ItemStack> slots = event.world().getDisplaySlots(ws);
        int slot = DisplayFit.slotFor(slots, item);
        if (slot == DisplayFit.NO_SLOT || !event.world().setDisplaySlot(ws, slot, item.copyWithCount(1))) {
            // The item was already taken from the villager: drop it here rather than destroy it
            // (dev playtest 2026-10-30: a full frame ate a sword every ~25 s).
            event.world().dropItem(ws, item.copyWithCount(1));
            QT.JOB_LOGGER.error("No free display slot for {} @ {} (slots {}): dropped it there", item, ws, slots);
            return ctxOut;
        }
        QT.JOB_LOGGER.info("Displayed {} on the display at {} (slot {})", item, ws, slot);
        return ctxOut;
    }
}
