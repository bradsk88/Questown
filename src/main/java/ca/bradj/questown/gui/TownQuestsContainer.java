package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.ContainerTypesInit;
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
        super(ContainerTypesInit.TOWN_QUESTS.get(), windowId);
        this.quests = quests;
    }

    public TownQuestsContainer(int windowId, Inventory inv, FriendlyByteBuf data) {
        this(windowId, data.readCollection(c -> new ArrayList<>(), buf -> {
            ResourceLocation recipeID = buf.readResourceLocation();
            return new RoomRecipe.Serializer().fromNetwork(recipeID, buf);
        }));
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<RoomRecipe> GetQuests() {
        return this.quests;
    }
}