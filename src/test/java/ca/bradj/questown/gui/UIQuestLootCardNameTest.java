package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the LOOT-quest card name: the "raw food"
 * title was wrong for the {@code #questown:villager_food} quest (cooked food
 * plus apples/carrots), so the name is the neutral "Food"
 * ({@code menu.quests.food}). No vanilla bootstrap needed: the LOOT name is a
 * bare translatable key.
 */
public class UIQuestLootCardNameTest {

    @Test
    public void loot_card_name_is_food_key() {
        UIQuest q = new UIQuest(
                UUID.randomUUID(),
                new ResourceLocation("questown:villager_food"),
                Quest.QuestType.LOOT,
                null,
                Quest.QuestStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null
        );
        assertEquals("menu.quests.food", q.getName().getString());
    }

    /**
     * The first loot quest is titled "Raw Food" and the cook's 8x quest "Food": the hunter's 1x quest
     * wants the raw tag, the cook's wants #questown:villager_food.
     */
    @Test
    public void raw_food_loot_card_name_is_raw_food_key() {
        UIQuest q = new UIQuest(
                UUID.randomUUID(),
                new ResourceLocation("questown:villager_simple_furnace_food_raw"),
                Quest.QuestType.LOOT,
                null,
                Quest.QuestStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null
        );
        assertEquals("menu.quests.raw_food", q.getName().getString());
    }
}
