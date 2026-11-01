package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomQuestTooltipKeysTest {

    @Test
    void aNewRoomQuest_saysBuild() {
        assertEquals(RoomQuestTooltipKeys.BUILD, RoomQuestTooltipKeys.forQuest(null));
    }

    @Test
    void anUpgradeQuest_saysUpgrade() {
        // Upgrade quests say "upgrade an existing room by adding the blocks shown here", not
        // "Build a room containing the blocks shown here".
        assertEquals(RoomQuestTooltipKeys.UPGRADE,
                RoomQuestTooltipKeys.forQuest(new ResourceLocation("questown", "store_room")));
    }
}
