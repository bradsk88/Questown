package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class RoomQuestTooltipKeys {

    public static final String BUILD = "menu.quests.room_quest";
    public static final String UPGRADE = "menu.quests.room_upgrade_quest";

    private RoomQuestTooltipKeys() {
    }

    /** The tooltip for a room quest: build a room, or upgrade an existing one. */
    public static String forQuest(@Nullable ResourceLocation fromRecipe) {
        return fromRecipe == null ? BUILD : UPGRADE;
    }
}
