package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public class TownBlockofProgressMenu extends AbstractContainerMenu implements FlagTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.BOP);
    final int blocksOfProgressCount;
    private FlagInfo flagInfo;

    public static TownBlockofProgressMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menus = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menus.bopMenu;
    }


    public <S extends IStatus<S>> TownBlockofProgressMenu(
            int windowId,
            FlagInfo flagInfo,
            int blocksOfProgress
    ) {
        super(MenuTypesInit.BLOCKS_OF_PROGRESS.get(), windowId);
        this.blocksOfProgressCount = blocksOfProgress;
        this.flagInfo = flagInfo;
    }

    public static void write(
            FriendlyByteBuf data,
            FlagInfo fi,
            int blocksOfProgressCount
    ) {
        fi.write(data);
        data.writeInt(blocksOfProgressCount);
    }

    public static int read(FriendlyByteBuf buf) {
        FlagInfo.read(buf);
        return buf.readInt();
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
