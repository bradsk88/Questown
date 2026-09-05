package ca.bradj.questown.gui;

import ca.bradj.questown.blocks.BlockOfProgress;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.JobID;
import java.util.Optional;
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

    public static @NotNull ItemStack syncConsumedBOP(
            ServerPlayer sender
    ) {
        // Also clear the BOP slot in the open menu, if present, to prevent it being returned to the player.
        // Returns the consumed stack so callers can read bless/guarantee NBT before it is voided.
        ItemStack consumed = ItemStack.EMPTY;
        if (sender.containerMenu instanceof JobChangeConfirmMenu menu) {
            consumed = menu.tx.takeBopSlot();
        }
        if (sender.containerMenu instanceof JobUnlockConfirmMenu menu) {
            consumed = menu.tx.takeBopSlot();
        }
        return consumed;
    }

    private @NotNull Slot getBopSlot(
    ) {
        Slot bopSlot = getSlot.apply(slotIndex);
        return bopSlot;
    }

    public boolean hasBlockOfProgress() {
        return getBopSlotItem().is(ItemsInit.BLOCK_OF_PROGRESS.get());
    }

    private @NotNull ItemStack getBopSlotItem() {
        return getBopSlot().getItem();
    }

    /** Empties the BOP slot and returns what was in it (EMPTY if none). */
    public @NotNull ItemStack takeBopSlot() {
        ItemStack stack = getBopSlotItem();
        getBopSlot().set(ItemStack.EMPTY);
        return stack;
    }

    public boolean isBlessed() {
        return BlockOfProgress.isBlessed(getBopSlotItem());
    }

    /** The root job a blessed BOP in the slot guarantees, or empty for a plain BOP. */
    public Optional<JobID> getBlessedJob() {
        return BlockOfProgress.getBlessedJob(getBopSlotItem());
    }
}
