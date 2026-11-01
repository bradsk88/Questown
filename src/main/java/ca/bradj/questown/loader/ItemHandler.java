package ca.bradj.questown.loader;

/** A loader-neutral mutable item handler (the loader's {@code IItemHandler} and {@code IItemHandlerModifiable}). */
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
