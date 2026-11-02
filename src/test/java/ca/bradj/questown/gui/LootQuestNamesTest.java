package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A LOOT quest's card name: the miner's coal quest says coal. The cook's food quest wants cooked food only. */
class LootQuestNamesTest {

    @Test
    void foodTags_haveTheirOwnNames() {
        Assertions.assertEquals("menu.quests.raw_food",
                LootQuestNames.key(new ResourceLocation("questown", "villager_simple_furnace_food_raw")));
        Assertions.assertEquals("menu.quests.food", LootQuestNames.key(new ResourceLocation("questown", "villager_food")));
        Assertions.assertEquals("menu.quests.cooked_food",
                LootQuestNames.key(new ResourceLocation("questown", "villager_cooked_food")));
    }

    @Test
    void anItem_usesTheItemsOwnName() {
        Assertions.assertNull(LootQuestNames.key(new ResourceLocation("minecraft", "coal")));
    }
}
