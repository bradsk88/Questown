package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class AddItemToContainerSpecialRule extends
        JobPhaseModifier {

    public AddItemToContainerSpecialRule() {

    }

    @Override
    public <CONTEXT> @Nullable CONTEXT afterInsertItem(
            CONTEXT ctxInput,
            AfterInsertItemEvent event
    ) {
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        BlockPos ws = event.workSpot().workPosition();
        BlockEntity be = event.level().getBlockEntity(ws);
        LazyOptional<IItemHandler> cap = be.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        if (cap == null || !cap.isPresent()) {
            QT.JOB_LOGGER.error("Work spot cannot accept items. " + getClass().getName() + " will not succeed.");
            return ctxOut;
        }
        Optional<IItemHandler> res = cap.resolve();
        if (res.isEmpty()) {
            QT.JOB_LOGGER.error("Work spot cannot accept items. " + getClass().getName() + " will not succeed. (2)");
            return ctxOut;
        }
        int amount = 1; // TODO: Implement "stacker"
        Compat.insertInNextOpenSlot(res.get(), event.inserted(), amount);
        return ctxOut;
    }
}
