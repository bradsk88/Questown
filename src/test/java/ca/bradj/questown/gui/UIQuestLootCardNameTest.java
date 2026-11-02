package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A LOOT quest card's name. */
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

    /** The first loot quest is "Raw Food"; the cook's is "Food". */
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
