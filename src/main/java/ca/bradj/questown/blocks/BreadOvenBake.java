package ca.bradj.questown.blocks;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bread oven's baking: 1 wheat and 1 coal make 1 bread over {@link #BAKE_TICKS} ticks. Changing
 * a slot restarts the bake. Used in real time and during a warp.
 */
// TODO: Make bread take 2 wheat once a job can insert into a slot that already holds an item, or gate on
//  a slot holding fewer than N; today an insert needs the wheat slot empty.
public final class BreadOvenBake {

    public static final int BAKE_TICKS = 1000;
    /** Upper bound of simulated ticks per single {@link #advance} call. */
    public static final int MAX_ADVANCE_TICKS = 13_000;

    public static final int SLOT_WHEAT = 0;
    public static final int SLOT_COAL = 1;
    public static final int SLOT_BREAD = 2;

    private final List<ItemStack> items;
    private int progress;

    public BreadOvenBake(List<ItemStack> items) {
        this.items = items;
    }

    public ItemStack getItem(int index) {
        return items.get(index);
    }

    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
    }

    public int progress() {
        return progress;
    }

    public void loadProgress(int progress) {
        this.progress = progress;
    }

    public boolean canBake() {
        ItemStack bread = getItem(SLOT_BREAD);
        boolean outputRoom = bread.isEmpty()
                || (bread.getItem() == Items.BREAD && bread.getCount() < bread.getMaxStackSize());
        return isWheat(getItem(SLOT_WHEAT))
                && isCoal(getItem(SLOT_COAL))
                && outputRoom;
    }

    /** @return true when this tick finished a bread */
    public boolean advanceOneTick() {
        if (!canBake()) {
            progress = 0;
            return false;
        }
        progress++;
        if (progress >= BAKE_TICKS) {
            progress = 0;
            setItem(SLOT_WHEAT, consumeOne(getItem(SLOT_WHEAT)));
            setItem(SLOT_COAL, consumeOne(getItem(SLOT_COAL)));
            setItem(SLOT_BREAD, produceOne(getItem(SLOT_BREAD)));
            return true;
        }
        return false;
    }

    /** Bakes for up to {@code ticks} ticks. @return true when at least one bread finished */
    public boolean advance(int ticks) {
        int capped = Math.min(ticks, MAX_ADVANCE_TICKS);
        boolean completed = false;
        for (int i = 0; i < capped; i++) {
            if (!canBake()) {
                progress = 0;
                break;
            }
            completed |= advanceOneTick();
        }
        return completed;
    }

    /** Bread taken from the output slot, or null when the slot is empty. */
    public @Nullable ItemStack takeBread(int count) {
        ItemStack bread = getItem(SLOT_BREAD);
        if (bread.isEmpty()) {
            return null;
        }
        int take = Math.min(count, bread.getCount());
        int remaining = bread.getCount() - take;
        setItem(SLOT_BREAD, remaining > 0 ? ca.bradj.questown.mc.Compat.copyWithCount(bread, remaining) : ItemStack.EMPTY);
        return ca.bradj.questown.mc.Compat.copyWithCount(bread, take);
    }

    public static boolean isWheat(ItemStack stack) {
        return stack.getItem() == Items.WHEAT;
    }

    public static boolean isCoal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.COALS);
    }

    private static ItemStack consumeOne(ItemStack stack) {
        return stack.getCount() > 1 ? ca.bradj.questown.mc.Compat.copyWithCount(stack, stack.getCount() - 1) : ItemStack.EMPTY;
    }

    private static ItemStack produceOne(ItemStack output) {
        return output.isEmpty()
                ? new ItemStack(Items.BREAD, 1)
                : ca.bradj.questown.mc.Compat.copyWithCount(output, output.getCount() + 1);
    }
}
