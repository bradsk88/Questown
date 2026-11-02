package ca.bradj.questown.town.special;

import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The empty-room recipe has no ingredients, so it matches any room with a registered door. */
class EmptyRoomRecipeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Not a static field: it must load after the vanilla bootstrap.
    private RoomRecipe emptyRoomRecipe() {
        return new RoomRecipe(new ResourceLocation("questown", "empty_room"), NonNullList.create(), 1, false);
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
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }

            @Override
            public void clearContent() {
            }
        };
    }

    @Test
    void zeroIngredientRecipeMatchesAnEmptyRoom() {
        assertTrue(emptyRoomRecipe().matches(of(), null),
                "a zero-ingredient room recipe must match a room with no contents (vacuous superset)");
    }

    @Test
    void zeroIngredientRecipeMatchesAFullRoom() {
        assertTrue(emptyRoomRecipe().matches(of(new ItemStack(Items.STONE)), null),
                "the empty-room recipe must also match a room that later gains contents");
    }

    @Test
    void emptyRoomRecipeIdMatchesTheQuestId() {
        assertTrue(SpecialQuests.EMPTY_ROOM.equals(new ResourceLocation("questown", "empty_room")),
                "the quest's wanted id must equal the room recipe's id for getRoomsMatching to find it");
    }
}
