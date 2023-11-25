package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;

public class TownWorkContainer extends AbstractContainerMenu {

    private final Collection<UIWork> work;
    public final ca.bradj.questown.gui.AddWorkContainer addWorkContainer;

    public static TownWorkContainer ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        AddWorkContainer qMenu = new AddWorkContainer(
                windowId,
                AddWorkContainer.readWorkResults(buf),
                AddWorkContainer.readFlagPosition(buf)
        );
        return new TownWorkContainer(windowId, readWork(buf), qMenu);
    }

    public TownWorkContainer(
            int windowId,
            Collection<UIWork> quests,
            AddWorkContainer awc
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
        this.addWorkContainer = awc;
    }

    public static Collection<UIWork> readWork(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<UIWork> r = data.readCollection(
                c -> new ArrayList<>(size),
                buf -> new UIWork.Serializer().fromNetwork(buf)
        );
        return r;
    }

    public static void writeWork(Collection<Ingredient> requestedResults, FriendlyByteBuf data) {
        data.writeInt(requestedResults.size());
        data.writeCollection(requestedResults, (buf, w) -> w.toNetwork(buf));
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIWork> getWork() {
        return work;
    }
}