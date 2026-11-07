package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** A recipe with an unlocking quest shows once that quest is posted; the others always show. */
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
