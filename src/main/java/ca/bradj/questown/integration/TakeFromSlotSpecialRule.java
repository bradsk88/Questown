package ca.bradj.questown.integration;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mc.Util;
import net.minecraft.core.BlockPos;
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
        BlockPos workPos = event.workSpot();
        BlockEntity entity = event.level().getBlockEntity(workPos);
        CONTEXT ctxOut = super.beforeExtract(ctxInput, event);
        // ctxOut is null if parent returns null (the default), use ctxInput in that case
        CONTEXT ctxBefore = ctxOut != null ? ctxOut : ctxInput;
        if (entity == null) {
            // During warp, cooking is handled by EagerCookResolver at the start of warp.
            // This rule is a no-op during warp - just return context unchanged.
            QT.BLOCK_LOGGER.debug(
                    "{}: BlockEntity at {} is null (during warp), cooking handled by EagerCookResolver.",
                    getClass(),
                    Util.getTinyString(workPos)
            );
            return ctxBefore;
        }
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
