package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

/** The flag's Settings tab: town-level actions that are not crafting (moving the town). */
public class FlagSettingsMenu extends AbstractContainerMenu implements FlagTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = FlagTabs.allExcept(OpenFlagMenuMessage.SETTINGS);
    private final FlagInfo flagInfo;

    public static FlagSettingsMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menus = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menus.settingsMenu;
    }

    public FlagSettingsMenu(int windowId, FlagInfo flagInfo) {
        super(MenuTypesInit.FLAG_SETTINGS.get(), windowId);
        this.flagInfo = flagInfo;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public FlagInfo getFlagInfo() {
        return flagInfo;
    }
}
