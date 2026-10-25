package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A LOOT (produced) quest's card name. Every LOOT quest that was not the raw-food tag read "Food":
 * the miner's "Coal x3" (r16 #7) showed "Food (n/3)". r17 (dev 2026-10-25) also moved the cook's
 * food quest to a cooked-food tag ("otherwise the food quest can technically be completed by lucky
 * hunters and gatherers").
 */
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
