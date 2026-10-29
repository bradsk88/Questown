package ca.bradj.questown.integration.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An armor stand's armor slots, or an item frame's one item, as a vanilla {@link Container}, so a
 * display counts as town storage (dev 2026-10-28: armor on a stand "count[s] as storage").
 * Armor stand slots run feet, legs, chest, head.
 */
public abstract class DisplaySlots implements Container {

    public static final List<EquipmentSlot> ARMOR_STAND_SLOTS = List.of(
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    );

    /** The display standing (or hanging) at pos, if any. */
    public static @Nullable DisplaySlots at(
            Level level,
            BlockPos pos
    ) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(pos), x -> of(x) != null)) {
            if (e.blockPosition().equals(pos)) {
                return of(e);
            }
        }
        return null;
    }

    public static @Nullable DisplaySlots of(Entity e) {
        if (e instanceof ArmorStand stand) {
            return new Stand(stand);
        }
        if (e instanceof ItemFrame frame) {
            return new Frame(frame);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack removeItem(
            int slot,
            int amount
    ) {
        ItemStack held = getItem(slot);
        if (held.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = held.copyWithCount(Math.min(amount, held.getCount()));
        setItem(slot, held.copyWithCount(held.getCount() - taken.getCount()));
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack held = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return held;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, ItemStack.EMPTY);
        }
    }

    private static final class Stand extends DisplaySlots {
        private final ArmorStand stand;

        private Stand(ArmorStand stand) {
            this.stand = stand;
        }

        @Override
        public int getContainerSize() {
            return ARMOR_STAND_SLOTS.size();
        }

        @Override
        public ItemStack getItem(int slot) {
            return stand.getItemBySlot(ARMOR_STAND_SLOTS.get(slot));
        }

        @Override
        public void setItem(
                int slot,
                ItemStack stack
        ) {
            stand.setItemSlot(ARMOR_STAND_SLOTS.get(slot), stack);
        }
    }

    private static final class Frame extends DisplaySlots {
        private final ItemFrame frame;

        private Frame(ItemFrame frame) {
            this.frame = frame;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public ItemStack getItem(int slot) {
            return frame.getItem();
        }

        @Override
        public void setItem(
                int slot,
                ItemStack stack
        ) {
            frame.setItem(stack);
        }
    }
}
