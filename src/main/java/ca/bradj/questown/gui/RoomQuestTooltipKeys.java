package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The tooltip line for a plain ROOM quest card: building a room vs upgrading one. */
public final class RoomQuestTooltipKeys {

    public static final String BUILD = "menu.quests.room_quest";
    public static final String UPGRADE = "menu.quests.room_upgrade_quest";

    private RoomQuestTooltipKeys() {
    }

    /**
     * An upgrade quest (it names the recipe it upgrades from) asks the player to add to an
     * existing room, not to build one (dev, 2026-10-19 playtest).
     */
    public static String forQuest(@Nullable ResourceLocation fromRecipe) {
        return fromRecipe == null ? BUILD : UPGRADE;
    }
}
