package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Collection;

public class TownQuestsContainer extends AbstractContainerMenu {

    private final Collection<UIQuest> quests;

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId);
        this.quests = quests;
    }

    public TownQuestsContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, readQuests(data));
    }

    public static Collection<UIQuest> readQuests(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<UIQuest> r = data.readCollection(c -> new ArrayList<>(size), buf -> {
            ResourceLocation recipeID = buf.readResourceLocation();
            return new UIQuest.Serializer().fromNetwork(recipeID, buf);
        });
        r.sort(UIQuest::compareTo);
        return r;
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIQuest> GetQuests() {
        return this.quests;
    }
}