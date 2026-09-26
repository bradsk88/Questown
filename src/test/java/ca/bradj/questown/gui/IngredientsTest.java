package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.mc.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void asIngredient_registeredItemId_yieldsItemIngredient_thatMatchesOnlyThatItem() {
        Ingredient ing = Ingredients.asIngredient(Compat.getItemId(Items.MUTTON));
        assertFalse(Ingredients.isTag(ing));
        assertTrue(ing.test(new ItemStack(Items.MUTTON)));
        // An ITEM quest with an exact item still matches only that item, not siblings.
        assertFalse(ing.test(new ItemStack(Items.PORKCHOP)));
    }

    @Test
    void asIngredient_tagId_yieldsTagIngredient_forThatTag() {
        Ingredient ing = Ingredients.asIngredient(TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location());
        assertTrue(Ingredients.isTag(ing));
        assertEquals("questown:villager_simple_furnace_food_raw", Ingredients.getTag(ing));
    }

    @Test
    void asIngredient_unknownId_yieldsTagIngredient_thatMatchesNothing() {
        // A missing id fails safe: it resolves to a tag ingredient with no members,
        // instead of throwing.
        Ingredient ing = Ingredients.asIngredient(new ResourceLocation("questown", "no_such_tag"));
        assertTrue(Ingredients.isTag(ing));
        assertFalse(ing.test(new ItemStack(Items.MUTTON)));
    }
}
