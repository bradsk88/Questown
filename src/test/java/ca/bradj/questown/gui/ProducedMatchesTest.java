package ca.bradj.questown.gui;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * r17 (dev 2026-10-25): on the "jobs that make this item" screen, the Produces row listed the
 * job's whole loot (the hunter's leather, bones, ...) after a click on raw food; it should show
 * only the items that match what was clicked.
 */
class ProducedMatchesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsOnlyMatchingResults() {
        List<ItemStack> out = ProducedMatches.of(
                List.of(new ItemStack(Items.LEATHER), new ItemStack(Items.MUTTON), new ItemStack(Items.BONE)),
                Ingredient.of(Items.MUTTON, Items.BEEF)
        );

        Assertions.assertEquals(1, out.size());
        Assertions.assertTrue(out.get(0).is(Items.MUTTON));
    }

    @Test
    void nothingMatches_showsEverything() {
        List<ItemStack> results = List.of(new ItemStack(Items.LEATHER), new ItemStack(Items.BONE));

        Assertions.assertEquals(2, ProducedMatches.of(results, Ingredient.of(Items.MUTTON)).size());
    }
}
