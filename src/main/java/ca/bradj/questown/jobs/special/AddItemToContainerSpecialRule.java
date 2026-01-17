package ca.bradj.questown.jobs.special;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
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
            AfterInsertItemEvent<CONTEXT> event
    ) {
        CONTEXT ctxOut = super.afterInsertItem(ctxInput, event);
        // ctxOut is null if parent returns null (the default), use ctxInput in that case
        CONTEXT ctx = ctxOut != null ? ctxOut : ctxInput;
        BlockPos ws = event.workSpot().workPosition();
        BlockEntity be = event.level().getBlockEntity(ws);
        if (be == null) {
            // During warp, deposit directly to containers (not virtual work blocks)
            // This allows subsequent jobs to collect the items via withContainerItemRemoved
            if (ctx instanceof MCTownState townState) {
                QT.JOB_LOGGER.info(
                        "AddItemToContainerSpecialRule: Depositing to containers during warp, item: {}",
                        event.inserted()
                );
                // Use depositItems to add to real containers in MCTownState
                MCHeldItem heldItem = MCHeldItem.fromMCItemStack(event.inserted());
                ImmutableList<MCHeldItem> notDeposited = townState.depositItems(ImmutableList.of(heldItem));
                if (!notDeposited.isEmpty()) {
                    QT.JOB_LOGGER.warn(
                            "AddItemToContainerSpecialRule: Could not deposit {} during warp (containers full?)",
                            event.inserted()
                    );
                }
                // Return the townState - depositItems mutates containers in place
                @SuppressWarnings("unchecked")
                CONTEXT result = (CONTEXT) townState;
                return result;
            }
            QT.JOB_LOGGER.debug("BlockEntity at {} is null (likely during warp), skipping world container interaction", ws);
            return ctx;
        }
        LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
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
        if (!Compat.insertInNextOpenSlot(res.get(), event.inserted(), amount)) {
            QT.JOB_LOGGER.error("Item lost due to not enough space in target container @ {}: {}", ws, event.inserted());
        }
        return ctxOut;
    }
}
