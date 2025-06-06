package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public class MultiStatusMenu extends AbstractContainerMenu implements FlagTabsEmbedding {


    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenFlagMenuMessage.QUESTS,
            OpenFlagMenuMessage.ECONOMICS
    );
    private final FlagInfo flagInfo;

    public static MultiStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menu = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menu.villagersMenu;
    }

    public MultiStatusMenu(
            int windowId,
            FlagInfo flagPos,
            Runnable triggerAdvancement
    ) {
        super(MenuTypesInit.MULTI_VILLAGER.get(), windowId);
        this.flagInfo = flagPos;
        triggerAdvancement.run();
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int i
    ) {
        return ItemStack.EMPTY;
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
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
