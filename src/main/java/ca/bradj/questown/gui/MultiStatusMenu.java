package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

public class MultiStatusMenu extends AbstractContainerMenu implements FlagTabsEmbedding {


    private static final Collection<String> ENABLED_TABS = FlagTabs.allExcept(OpenFlagMenuMessage.VILLAGERS);
    private final FlagInfo flagInfo;

    public static MultiStatusMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menu = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menu.villagersMenu;
    }

    // Server side only: re-sends the statuses so work bars move while the tab is open. Null on the client.
    private final @Nullable Runnable resendStatuses;
    private int ticksSinceResend;
    private static final int RESEND_EVERY_TICKS = 20;

    public MultiStatusMenu(
            int windowId,
            FlagInfo flagPos,
            Runnable triggerAdvancement
    ) {
        this(windowId, flagPos, triggerAdvancement, null);
    }

    public MultiStatusMenu(
            int windowId,
            FlagInfo flagPos,
            Runnable triggerAdvancement,
            @Nullable Runnable resendStatuses
    ) {
        super(MenuTypesInit.MULTI_VILLAGER.get(), windowId);
        this.flagInfo = flagPos;
        this.resendStatuses = resendStatuses;
        triggerAdvancement.run();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (resendStatuses == null || ++ticksSinceResend < RESEND_EVERY_TICKS) {
            return;
        }
        ticksSinceResend = 0;
        resendStatuses.run();
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
