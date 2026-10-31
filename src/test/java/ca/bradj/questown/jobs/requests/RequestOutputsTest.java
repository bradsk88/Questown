package ca.bradj.questown.jobs.requests;

import ca.bradj.questown.mc.Compat;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * r16 #18 (dev 2026-10-24): the job board's picker split the cook's
 * {@code "initial_request": "#questown:villager_food"} into one tile per food in the tag, so it
 * offered carrot and apple though no job makes them. A tag request now stays one ingredient (the
 * picker's cycling Food tile).
 */
class RequestOutputsTest {

    private static final TagKey<Item> VILLAGER_FOOD = Compat.itemTag(
            new ResourceLocation("questown", "villager_food"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.CARROT.builtInRegistryHolder().bindTags(Set.of(VILLAGER_FOOD));
        Items.BREAD.builtInRegistryHolder().bindTags(Set.of(VILLAGER_FOOD));
    }

    @Test
    void aTagRequestStaysOneIngredient() {
        Ingredient food = Ingredient.of(VILLAGER_FOOD);

        Assertions.assertEquals(List.of(food), RequestOutputs.of(food));
    }

    @Test
    void anItemRequestListsItsItem() {
        List<Ingredient> out = RequestOutputs.of(Ingredient.of(Items.STICK));

        Assertions.assertEquals(1, out.size());
        Assertions.assertTrue(out.get(0).test(Items.STICK.getDefaultInstance()));
    }

    @Test
    void noRequestListsNothing() {
        Assertions.assertEquals(List.of(), RequestOutputs.of(null));
    }
}
