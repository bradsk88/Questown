package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public class VillagerStatsMenu extends AbstractContainerMenu implements StatusListener {
    private final DataSlot statusSlot;
    private final VillagerMenus menus;

    public static VillagerStatsMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.statsMenu;
    }


    public <S extends IStatus<S>> VillagerStatsMenu(
            int windowId,
            VisitorMobEntity visitor,
            VillagerMenus menus
    ) {
        super(MenuTypesInit.VILLAGER_STATS.get(), windowId);

        this.addDataSlot(this.statusSlot = DataSlot.standalone());
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(visitor.getStatusForServer()));

        visitor.addStatusListener(this);
        this.menus = menus;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    public IStatus<?> getStatus() {
        return SessionUniqueOrdinals.getStatus(this.statusSlot.get());
    }

    @Override
    public void statusChanged(IStatus<?> newStatus) {
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(newStatus));
    }

    public TownQuestsContainer questsMenu() {
        return menus.questsMenu;
    }

    public InventoryAndStatusMenu invMenu() {
        return menus.invMenu;
    }
}
