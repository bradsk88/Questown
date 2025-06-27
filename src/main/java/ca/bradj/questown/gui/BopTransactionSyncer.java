package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class BopTransactionSyncer {
    private final Function<Integer, Slot> getSlot;


    // First slot index after four rows of 9.
    private static final int slotIndex = 9 * 4;
    // 35 is the last index of these rows, so 36 is BOP slot.

    public BopTransactionSyncer(Function<Integer, Slot> getSlot) {
        this.getSlot = getSlot;
    }

    public static void syncConsumedBOP(
            ServerPlayer sender
    ) {
        // Also clear the BOP slot in the open menu, if present, to prevent it being returned to the player
        if (sender.containerMenu instanceof JobChangeConfirmMenu menu) {
            menu.tx.clearBopSlot();
        }
        if (sender.containerMenu instanceof JobUnlockConfirmMenu menu) {
            menu.tx.clearBopSlot();
        }
    }

    private @NotNull Slot getBopSlot(
    ) {
        Slot bopSlot = getSlot.apply(slotIndex);
        return bopSlot;
    }

    public boolean hasBlockOfProgress() {
        Slot bopSlot = getBopSlot();
        return bopSlot.getItem().is(ItemsInit.BLOCK_OF_PROGRESS.get());
    }

    public void clearBopSlot() {
        getBopSlot().set(ItemStack.EMPTY);
    }
}
