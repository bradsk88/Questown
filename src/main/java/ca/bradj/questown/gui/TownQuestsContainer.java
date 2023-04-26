package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Collection;

public class TownQuestsContainer extends AbstractContainerMenu {

    private final Collection<RoomRecipe> quests;

    public TownQuestsContainer(
            int windowId,
            Collection<RoomRecipe> quests
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId);
        this.quests = quests;
    }

    public TownQuestsContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, read(data));
    }

    private static Collection<RoomRecipe> read(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<RoomRecipe> r = data.readCollection(c -> new ArrayList<>(size), buf -> {
            ResourceLocation recipeID = buf.readResourceLocation();
            RoomRecipe roomRecipe = new RoomRecipe.Serializer().fromNetwork(recipeID, buf);
            return roomRecipe;
        });
        r.sort(RoomRecipe::compareTo);
        return r;
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<RoomRecipe> GetQuests() {
        return this.quests;
    }
}