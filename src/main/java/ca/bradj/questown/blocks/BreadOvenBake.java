package ca.bradj.questown.blocks;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registry-free bake state machine for the bread oven: 1 wheat + 1 coal
 * &rarr; 1 bread over {@link #BAKE_TICKS} ticks, with the partial-progress
 * reset rule (a slot change interrupts the bake — mirroring the furnace).
 *
 * <p>Single source of truth shared by BOTH lanes so the warp can never
 * drift from real time: the realtime {@link BreadOvenBlockEntity} (whose
 * NBT-persisted slots it mutates) and the warp lane
 * ({@code WarpWorldAccess.advanceBreadOven}, which runs it against its
 * in-memory slot snapshot). The machine operates on the slot list it is
 * given and only owns the progress counter.
 */
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

    /** Ticks accumulated toward the in-flight bake (0 when idle). */
    public int progress() {
        return progress;
    }

    /** NBT hydration hook: restore a persisted partial bake. */
    public void loadProgress(int progress) {
        this.progress = progress;
    }

    /** True while this oven can still make progress on the next bake. */
    public boolean canBake() {
        ItemStack bread = getItem(SLOT_BREAD);
        boolean outputRoom = bread.isEmpty()
                || (bread.getItem() == Items.BREAD && bread.getCount() < bread.getMaxStackSize());
        return isWheat(getItem(SLOT_WHEAT))
                && isCoal(getItem(SLOT_COAL))
                && outputRoom;
    }

    /**
     * One bake step.
     *
     * @return true iff this tick completed a bake (slot contents changed)
     */
    public boolean advanceOneTick() {
        if (!canBake()) {
            // Bake interrupted (a slot changed) — the partial progress is not kept.
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

    /**
     * Up to {@code ticks} bake steps (warp lane). Stops as soon as no
     * further bake is possible.
     *
     * @return true iff at least one bake completed (slot contents changed)
     */
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
        setItem(SLOT_BREAD, remaining > 0 ? bread.copyWithCount(remaining) : ItemStack.EMPTY);
        return bread.copyWithCount(take);
    }

    public static boolean isWheat(ItemStack stack) {
        return stack.getItem() == Items.WHEAT;
    }

    public static boolean isCoal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.COALS);
    }

    private static ItemStack consumeOne(ItemStack stack) {
        return stack.getCount() > 1 ? stack.copyWithCount(stack.getCount() - 1) : ItemStack.EMPTY;
    }

    private static ItemStack produceOne(ItemStack output) {
        return output.isEmpty()
                ? new ItemStack(Items.BREAD, 1)
                : output.copyWithCount(output.getCount() + 1);
    }
}
