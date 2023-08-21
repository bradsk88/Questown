package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.HeldItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class MCHeldItem implements HeldItem<MCHeldItem, MCTownItem> {

    private final MCTownItem delegate;
    private boolean locked = false;

    public MCHeldItem(
            MCTownItem item
    ) {
        this(item, false);
    }

    public MCHeldItem(
            MCTownItem item,
            boolean locked
    ) {
        this.delegate = item;
        this.locked = locked;
    }

    public static MCHeldItem fromTag(CompoundTag tag) {
        CompoundTag nbt = tag.getCompound("item");
        ItemStack stack = ItemStack.of(nbt);
        MCTownItem ti = new MCTownItem(stack.getItem(), stack.getCount(), nbt);
        boolean loqued = tag.getBoolean("locked");
        return new MCHeldItem(ti, loqued);
    }

    public static MCHeldItem fromMCItemStack(ItemStack item) {
        return new MCHeldItem(MCTownItem.fromMCItemStack(item), false);
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean isFood() {
        return delegate.isFood();
    }

    @Override
    public MCHeldItem shrink() {
        return new MCHeldItem(delegate.shrink(), locked);
    }

    public static MCHeldItem Air() {
        return new MCHeldItem(MCTownItem.Air(), false);
    }

    public MCTownItem get() {
        return delegate;
    }

    @Override
    public MCHeldItem locked() {
        return new MCHeldItem(delegate, true);
    }

    @Override
    public MCHeldItem unlocked() {
        return new MCHeldItem(delegate, false);
    }

    public Tag serializeNBT() {
        CompoundTag tag = delegate.serializeNBT();
        tag.putBoolean("locked", locked);
        return tag;
    }

    public MCTownItem toItem() {
        return delegate;
    }

    @Override
    public String toString() {
        return "MCHeldItem{" +
                "delegate=" + delegate +
                ", locked=" + locked +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MCHeldItem that = (MCHeldItem) o;
        return locked == that.locked && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate, locked);
    }
}
