package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The lang key naming a LOOT (produced) quest's wanted tag, or null for an item, which uses the
 * item's own name. Every LOOT quest that was not the raw-food tag used to read "Food", the miner's
 * Coal quest included. Pinned by {@code LootQuestNamesTest}.
 */
public final class LootQuestNames {

    private static final Map<ResourceLocation, String> TAG_KEYS = Map.of(
            new ResourceLocation("questown", "villager_simple_furnace_food_raw"), "menu.quests.raw_food",
            new ResourceLocation("questown", "villager_food"), "menu.quests.food",
            new ResourceLocation("questown", "villager_cooked_food"), "menu.quests.cooked_food"
    );

    private LootQuestNames() {
    }

    public static @Nullable String key(ResourceLocation wantedId) {
        return TAG_KEYS.get(wantedId);
    }
}
