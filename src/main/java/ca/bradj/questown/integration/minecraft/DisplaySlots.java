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
import java.util.stream.IntStream;

/** An armor stand's armor slots or an item frame's item as a {@link Container}, so a display counts as storage. Armor slots run feet, legs, chest, head. */
public abstract class DisplaySlots implements Container {

    public static final List<EquipmentSlot> ARMOR_STAND_SLOTS = List.of(
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    );

    /** The display at pos, preferring one with a free slot (two corner frames share a position). */
    public static @Nullable DisplaySlots at(
            Level level,
            BlockPos pos
    ) {
        List<DisplaySlots> here = level.getEntitiesOfClass(Entity.class, new AABB(pos), DisplaySlots::isDisplay).stream()
                .filter(e -> e.blockPosition().equals(pos))
                .map(DisplaySlots::of)
                .toList();
        return preferFree(here);
    }

    public static int filledAt(Level level, BlockPos pos) {
        DisplaySlots d = at(level, pos);
        return d == null ? 0 : (int) d.items().stream().filter(i -> !i.isEmpty()).count();
    }

    static @Nullable DisplaySlots preferFree(List<DisplaySlots> here) {
        for (DisplaySlots d : here) {
            if (d.items().stream().anyMatch(ItemStack::isEmpty)) {
                return d;
            }
        }
        return here.isEmpty() ? null : here.get(0);
    }

    public static boolean isDisplay(Entity e) {
        return e instanceof ArmorStand || e instanceof ItemFrame;
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

    public List<ItemStack> items() {
        return IntStream.range(0, getContainerSize()).mapToObj(this::getItem).toList();
    }

    @Override
    public boolean isEmpty() {
        return items().stream().allMatch(ItemStack::isEmpty);
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
        ItemStack taken = ca.bradj.questown.mc.Compat.copyWithCount(held, Math.min(amount, held.getCount()));
        setItem(slot, ca.bradj.questown.mc.Compat.copyWithCount(held, held.getCount() - taken.getCount()));
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
