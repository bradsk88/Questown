package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.ItemHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Reverse bridge: exposes a neutral {@link ItemHandler} (the town flag's BOP deposit
 * handler) as a NeoForge {@code IItemHandler} so it can be registered as an item-handler
 * capability. Deposit only: deliberately does **not** implement
 * {@code IItemHandlerModifiable} — the flag's handler is not open to slot replacement,
 * and this class therefore never surfaces {@code setStackInSlot}.
 */
public final class TownFlagBOPHandlerBridge implements IItemHandler {

    private final ItemHandler delegate;

    public TownFlagBOPHandlerBridge(ItemHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }
}
