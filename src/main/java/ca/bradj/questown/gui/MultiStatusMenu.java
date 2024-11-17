package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class MultiStatusMenu extends AbstractContainerMenu {

    public static MultiStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        return new MultiStatusMenu(windowId);
    }

    public MultiStatusMenu(
            int windowId
    ) {
        super(MenuTypesInit.MULTI_VILLAGER.get(), windowId);
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }
}
