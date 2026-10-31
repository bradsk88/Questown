package ca.bradj.questown.jobs.special;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dev 2026-10-28: a display job puts each piece of armor in its own slot on a stand. */
class DisplayFitTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<ItemStack> emptyStand() {
        return new ArrayList<>(Collections.nCopies(4, ItemStack.EMPTY));
    }

    @Test
    void armorGoesInItsOwnSlot() {
        List<ItemStack> stand = emptyStand();
        Assertions.assertEquals(0, DisplayFit.slotFor(stand, new ItemStack(Items.LEATHER_BOOTS)));
        Assertions.assertEquals(1, DisplayFit.slotFor(stand, new ItemStack(Items.LEATHER_LEGGINGS)));
        Assertions.assertEquals(2, DisplayFit.slotFor(stand, new ItemStack(Items.LEATHER_CHESTPLATE)));
        Assertions.assertEquals(3, DisplayFit.slotFor(stand, new ItemStack(Items.LEATHER_HELMET)));
    }

    @Test
    void aFilledSlotTakesNothing() {
        List<ItemStack> stand = emptyStand();
        stand.set(0, new ItemStack(Items.LEATHER_BOOTS));
        Assertions.assertEquals(DisplayFit.NO_SLOT, DisplayFit.slotFor(stand, new ItemStack(Items.IRON_BOOTS)));
        Assertions.assertFalse(DisplayFit.anyFits(stand, List.of(new ItemStack(Items.LEATHER_BOOTS))));
        Assertions.assertTrue(DisplayFit.anyFits(stand, List.of(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.LEATHER_HELMET))));
    }

    @Test
    void aStandTakesNoSword_aFrameTakesOneItem() {
        Assertions.assertEquals(DisplayFit.NO_SLOT, DisplayFit.slotFor(emptyStand(), new ItemStack(Items.STONE_SWORD)));
        Assertions.assertEquals(0, DisplayFit.slotFor(List.of(ItemStack.EMPTY), new ItemStack(Items.STONE_SWORD)));
        Assertions.assertEquals(DisplayFit.NO_SLOT, DisplayFit.slotFor(List.of(new ItemStack(Items.WOODEN_SWORD)), new ItemStack(Items.STONE_SWORD)));
        Assertions.assertEquals(DisplayFit.NO_SLOT, DisplayFit.slotFor(List.of(), new ItemStack(Items.STONE_SWORD)));
    }
}
