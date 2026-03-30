package ca.bradj.questown.jobs;

import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.world.QTWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class SlotPrecondition {
    private final int slotIndex;
    private final Function<ItemStack, Boolean> compare;

    public SlotPrecondition(
            int slot,
            Function<ItemStack, Boolean> compare
    ) {
        this.slotIndex = slot;
        this.compare = compare;
    }

    public boolean test(@Nullable BlockEntity entity) {
        return getSlotValue(entity, slotIndex).map(compare).orElse(true);
    }

    public boolean test(QTWorldAccess world, BlockPos pos) {
        ItemStack slot = world.getContainerSlot(pos, slotIndex);
        return compare.apply(slot);
    }

    private static Optional<ItemStack> getSlotValue(
            @Nullable BlockEntity state,
            int slot
    ) {
        if (!(state instanceof Container c)) {
            return Optional.empty();
        }
        return Optional.of(c.getItem(slot));
    }

    public static Optional<SlotPrecondition> parse(@Nullable String stateStr) {
        if (stateStr == null) {
            return Optional.empty();
        }
        String[] eq = stateStr.split("/");
        if (eq.length > 1) {
            int slot = Integer.parseInt(eq[0]);
            boolean isAirCheck = eq[1].equals("minecraft:air");
            Predicate<ItemStack> check = ItemStack::isEmpty;
            if (!isAirCheck) {
                check = Ingredients.fromString(eq[1]);
            }
            return Optional.of(new SlotPrecondition(slot, check::test));
        }
        return Optional.empty();
    }
}
