package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Collection;

public class TownWorkContainer extends AbstractContainerMenu {

    private final Collection<UIWork> work;

    public TownWorkContainer(
            int windowId,
            Collection<UIWork> quests
    ) {
        super(MenuTypesInit.TOWN_WORK.get(), windowId);
        this.work = quests;
    }

    public TownWorkContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, readWork(data));
    }

    public static Collection<UIWork> readWork(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<UIWork> r = data.readCollection(
                c -> new ArrayList<>(size),
                buf -> new UIWork.Serializer().fromNetwork(buf)
        );
        return r;
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIWork> getWork() {
        return work;
    }
}