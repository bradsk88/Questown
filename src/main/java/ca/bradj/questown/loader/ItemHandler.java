package ca.bradj.questown.loader;

/**
 * A neutral, loader-agnostic view of a mutable item handler (Forge {@code IItemHandler}).
 * Main-code logic manipulates inventory slots through this interface; the active loader
 * provides a concrete implementation that wraps the loader's own handler and delegates.
 *
 * <p>Method set mirrors the loader {@code IItemHandler}+{@code IItemHandlerModifiable} so the
 * neo impl is a 1:1 delegation.
 */
public interface ItemHandler {
    int getSlots();

    net.minecraft.world.item.ItemStack getStackInSlot(int slot);

    void setStackInSlot(int slot, net.minecraft.world.item.ItemStack stack);

    net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate);

    net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack);

    void setChanged();
}
