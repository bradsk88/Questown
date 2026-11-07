package ca.bradj.questown.town.entity;

import ca.bradj.questown.mc.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** {@link TownFlagBOPItemHandler#depositOne} keeps the deposited item, even when it empties the villager's stack. */
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
        Compat.setItemDataTag(s, tag);
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
        Assertions.assertTrue(Compat.itemDataTag(s).getBoolean("questown_blessed"), "the bless NBT must survive");
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
