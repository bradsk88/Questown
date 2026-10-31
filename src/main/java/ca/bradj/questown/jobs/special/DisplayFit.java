package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.minecraft.DisplaySlots;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Which slot of a display an item goes in (dev 2026-10-28: a villager puts armor on a stand, or a
 * weapon in a frame, as its own job). A stand's slots are {@link DisplaySlots#ARMOR_STAND_SLOTS}
 * (four); a frame has one.
 */
public final class DisplayFit {

    public static final int NO_SLOT = -1;

    private DisplayFit() {
    }

    /** The empty slot the item fits, or {@link #NO_SLOT}. */
    public static int slotFor(
            List<ItemStack> slots,
            ItemStack item
    ) {
        if (item.isEmpty()) {
            return NO_SLOT;
        }
        if (slots.size() == 1) {
            return slots.get(0).isEmpty() ? 0 : NO_SLOT;
        }
        if (!isArmorStand(slots)) {
            return NO_SLOT;
        }
        // 1.19.2 has no Equipable: anything not wearable falls back to the main hand.
        net.minecraft.world.entity.EquipmentSlot slot = net.minecraft.world.entity.Mob.getEquipmentSlotForItem(item);
        if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
            return NO_SLOT;
        }
        int i = DisplaySlots.ARMOR_STAND_SLOTS.indexOf(slot);
        if (i < 0 || !slots.get(i).isEmpty()) {
            return NO_SLOT;
        }
        return i;
    }

    /** True when at least one of the items fits an empty slot. */
    public static boolean anyFits(
            List<ItemStack> slots,
            Collection<ItemStack> items
    ) {
        return items.stream().anyMatch(i -> slotFor(slots, i) != NO_SLOT);
    }

    /** The kind a display's slot list says it is: an armor stand has four, a frame one. */
    public static boolean isArmorStand(List<ItemStack> slots) {
        return slots.size() == DisplaySlots.ARMOR_STAND_SLOTS.size();
    }
}
