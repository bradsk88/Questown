package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LockableInventoryWrapper extends InvWrapper {
    private final List<DataSlot> lockedSlots;

    public LockableInventoryWrapper(Container gathererInv,
                                    List<DataSlot> lockedSlots
    ) {
        super(gathererInv);
        this.lockedSlots = lockedSlots;
    }

    @NotNull
    @Override
    public ItemStack insertItem(
            int slot,
            @NotNull ItemStack stack,
            boolean simulate
    ) {
        ItemStack result = super.insertItem(slot, stack, simulate);
        boolean transferredAny = !stack.equals(result);
        if (transferredAny) {
            Questown.LOGGER.debug("Marking slot {} as locked", slot);
            lockedSlots.get(slot).set(1); // 1 - locked
        }
        return result;
    }

    @NotNull
    @Override
    public ItemStack extractItem(
            int slot,
            int amount,
            boolean simulate
    ) {
        ItemStack extracted = super.extractItem(slot, amount, simulate);
        if (!extracted.isEmpty()) {
            lockedSlots.get(slot).set(0);
        }
        return extracted;
    }
}
