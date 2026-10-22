package ca.bradj.questown.town.entity;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The flag's BOP deposit step ({@link TownFlagBOPItemHandler#depositOne}). A villager
 * carries one BOP, so the deposit consumes the whole stack. Agent playtest 2026-10-22 r3:
 * the hunter deposited three blessed BOPs and the flag kept none of them (its save showed
 * one plain tutorial BOP, the menu said 2).
 *
 * <p>The item is a vanilla stand-in: {@code questown:block_of_progress} is registry-bound,
 * and the step is item-agnostic.
 */
class TownFlagBOPDepositTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack blessedStandIn() {
        ItemStack s = new ItemStack(Items.STONE);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("questown_blessed", true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    @Test
    void depositingTheLastItem_storesARealCopyWithItsBlessing() {
        ItemStack carried = blessedStandIn();
        List<ItemStack> stored = new ArrayList<>();

        ItemStack left = TownFlagBOPItemHandler.depositOne(carried, false, stored::add);

        Assertions.assertEquals(1, stored.size());
        ItemStack s = stored.get(0);
        Assertions.assertFalse(s.isEmpty(), "the stored BOP must not be ItemStack.EMPTY");
        Assertions.assertTrue(s.is(Items.STONE));
        Assertions.assertEquals(1, s.getCount());
        Assertions.assertTrue(s.has(DataComponents.CUSTOM_DATA), "the bless NBT must survive");
        Assertions.assertTrue(left.isEmpty());
    }

    @Test
    void simulate_storesNothingAndLeavesTheCallersStackAlone() {
        ItemStack carried = blessedStandIn();
        List<ItemStack> stored = new ArrayList<>();

        ItemStack left = TownFlagBOPItemHandler.depositOne(carried, true, stored::add);

        Assertions.assertTrue(stored.isEmpty());
        Assertions.assertEquals(1, carried.getCount(), "simulate must not consume the caller's stack");
        Assertions.assertTrue(left.isEmpty(), "the simulated remainder is what a real deposit leaves");
    }
}
