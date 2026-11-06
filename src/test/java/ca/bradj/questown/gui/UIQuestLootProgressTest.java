package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The "(n/m)" on a LOOT quest card, capped at the target, and absent when there is no progress. */
class UIQuestLootProgressTest {

    private static final ResourceLocation FOOD = new ResourceLocation("questown", "villager_food");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lootCard_showsExtractedOverTarget() {
        assertEquals("menu.quests.food (3/8)", uiQuest(3).getName().getString());
    }

    @Test
    void lootCard_capsAtTheTarget() {
        assertEquals("menu.quests.food (8/8)", uiQuest(12).getName().getString());
    }

    @Test
    void lootCard_noSuffixWhenProgressUncomputed() {
        assertEquals("menu.quests.food", uiQuest(null).getName().getString());
    }

    private static UIQuest uiQuest(Integer deposited) {
        return new UIQuest(
                UUID.randomUUID(),
                FOOD,
                Quest.QuestType.LOOT,
                eightIngredients(),
                Quest.QuestStatus.ACTIVE,
                null,
                null,
                null,
                null,
                deposited
        );
    }

    private static List<Ingredient> eightIngredients() {
        return java.util.Collections.nCopies(8, Ingredient.of(new ItemStack(Items.MUTTON)));
    }
}
