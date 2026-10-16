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

/**
 * Pins the "(n/target)" progress on the LOOT-quest card (2026-10-15 ruling):
 * the "Food" card shows how much of the count has been extracted so far —
 * the same "(n/8)" shape the ITEM quests render for deposits — capped at the
 * target, and absent when no progress was computed.
 */
class UIQuestLootProgressTest {

    private static final ResourceLocation FOOD = new ResourceLocation("questown", "villager_food");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void lootCard_showsExtractedOverTarget() {
        // 8 ingredients stand for the count-8 quest (ingredients.size() is the
        // target the card caps at); only the progress shape is under test.
        // The suffix carries a leading space (" (n/m)") - the 2026-10-15 playtest fix.
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
