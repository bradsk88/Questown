package ca.bradj.questown.town.special;

import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The empty-room recipe has no ingredients, so it matches any room with a registered door. */
class EmptyRoomRecipeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Path RECIPE = Path.of("src/main/resources/data/questown/recipes/empty_room.json");

    // Not a static field: it must load after the vanilla bootstrap.
    private RoomRecipe emptyRoomRecipe() throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(RECIPE)).getAsJsonObject();
        return new RoomRecipe.Serializer().codec().codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static Container of(ItemStack... items) {
        return new Container() {
            @Override
            public int getContainerSize() {
                return items.length;
            }

            @Override
            public boolean isEmpty() {
                return items.length == 0;
            }

            @Override
            public ItemStack getItem(int slot) {
                return slot < items.length ? items[slot] : ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItem(int slot, int count) {
                return ItemStack.EMPTY;
            }

            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
            }

            @Override
            public void setChanged() {
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clearContent() {
            }
        };
    }

    @Test
    void emptyRoomRecipe_matchesARoomWithNothingInIt() throws IOException {
        assertTrue(emptyRoomRecipe().matches(of(), null));
    }

    @Test
    void emptyRoomRecipe_stillMatchesOnceTheRoomHasContents() throws IOException {
        assertTrue(emptyRoomRecipe().matches(of(new ItemStack(Items.STONE)), null));
    }

    @Test
    void questId_isTheRecipeFileId_soTheQuestFindsTheRoom() {
        assertTrue(SpecialQuests.EMPTY_ROOM.equals(new ResourceLocation("questown", "empty_room")));
    }
}
