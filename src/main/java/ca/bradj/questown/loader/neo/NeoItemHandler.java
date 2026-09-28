package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ItemHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * NeoForge impl of the neutral {@link ItemHandler}; 1:1 delegation to the loader
 * {@code IItemHandler}+{@code IItemHandlerModifiable}.
 */
public final class NeoItemHandler implements ItemHandler {
    private final IItemHandler handler;
    private final IItemHandlerModifiable modifiable;

    public NeoItemHandler(IItemHandler handler) {
        this.handler = handler;
        this.modifiable = (handler instanceof IItemHandlerModifiable m) ? m : null;
    }

    @Override
    public int getSlots() {
        return handler.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return handler.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (modifiable == null) {
            throw new UnsupportedOperationException("Underlying handler is not modifiable (no setStackInSlot).");
        }
        modifiable.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return handler.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return handler.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return handler.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return handler.isItemValid(slot, stack);
    }

    @Override
    public void setChanged() {
        // NeoForge 1.20.6 handlers have no explicit setChanged; changes go through the BlockEntity.
    }
}
