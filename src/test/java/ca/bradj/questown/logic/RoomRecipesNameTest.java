package ca.bradj.questown.logic;

import ca.bradj.questown.Questown;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Room display names (RoomRecipes.getName): a block room is registered as
 * "block_room/block.&lt;ns&gt;.&lt;block&gt;" (RoomBlock.getRoomId) and has no "room.*"
 * lang key, so its name must fall through to the block's own lang key instead of
 * rendering as a raw key in the quest UI / room-created chat broadcast.
 */
class RoomRecipesNameTest {

    @Test
    void blockRoomUsesBlockLangKey() {
        ResourceLocation id = Questown.ResourceLocation(
                "block_room/block.questown.job_board_block");
        TranslatableContents contents = translatableContents(RoomRecipes.getName(id));
        assertEquals("block.questown.job_board_block", contents.getKey());
    }

    @Test
    void shapeRoomKeepsRoomKey() {
        TranslatableContents contents = translatableContents(
                RoomRecipes.getName(Questown.ResourceLocation("kitchen_small")));
        assertEquals("room.kitchen_small", contents.getKey());
    }

    private static TranslatableContents translatableContents(Component c) {
        assertTrue(c.getContents() instanceof TranslatableContents,
                "expected translatable contents, got " + c.getContents());
        return (TranslatableContents) c.getContents();
    }
}
