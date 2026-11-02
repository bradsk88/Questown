package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.minecraft.DisplaySlots;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/** Which slot of a display an item goes in: an armor stand has four, an item frame one. */
public final class DisplayFit {

    public static final int NO_SLOT = -1;

    private DisplayFit() {
    }

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
        Equipable equipable = Equipable.get(item);
        if (equipable == null) {
            return NO_SLOT;
        }
        int i = DisplaySlots.ARMOR_STAND_SLOTS.indexOf(equipable.getEquipmentSlot());
        if (i < 0 || !slots.get(i).isEmpty()) {
            return NO_SLOT;
        }
        return i;
    }

    public static boolean anyFits(
            List<ItemStack> slots,
            Collection<ItemStack> items
    ) {
        return items.stream().anyMatch(i -> slotFor(slots, i) != NO_SLOT);
    }

    public static boolean isArmorStand(List<ItemStack> slots) {
        return slots.size() == DisplaySlots.ARMOR_STAND_SLOTS.size();
    }
}
