package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * r17 #1 (dev 2026-10-25): "the mineshaft and fishing station crafting recipes should be hidden
 * until the relevant quests have been opened." A recipe with an unlocking quest shows once that
 * quest has been posted (open or completed); the others always show.
 */
class FlagCraftGatesTest {

    private static final ResourceLocation MINESHAFT = new ResourceLocation("questown", "block_room/block.questown.mineshaft");

    @Test
    void ungated_alwaysShows() {
        Assertions.assertTrue(FlagCraftGates.visible(null, List.of()));
    }

    @Test
    void gated_hiddenUntilItsQuestIsPosted() {
        Assertions.assertFalse(FlagCraftGates.visible(MINESHAFT, List.of(new ResourceLocation("questown", "bedroom"))));
        Assertions.assertTrue(FlagCraftGates.visible(MINESHAFT, List.of(MINESHAFT)));
    }
}
