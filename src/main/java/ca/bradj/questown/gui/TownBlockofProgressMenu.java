package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public class TownBlockofProgressMenu extends AbstractContainerMenu implements FlagTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = FlagTabs.allExcept(OpenFlagMenuMessage.BOP);
    final int blocksOfProgressCount;
    /** Root job ids of the deposited BOPs, in deposit order; "" for a plain BOP. */
    final ImmutableList<String> bopRootJobs;
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
            ImmutableList<String> bopRootJobs
    ) {
        super(MenuTypesInit.TOWN_BLOCKS_OF_PROGRESS.get(), windowId);
        this.blocksOfProgressCount = bopRootJobs.size();
        this.bopRootJobs = bopRootJobs;
        this.flagInfo = flagInfo;
    }

    public static void write(
            FriendlyByteBuf data,
            FlagInfo fi,
            ImmutableList<String> bopRootJobs
    ) {
        fi.write(data);
        data.writeInt(bopRootJobs.size());
        for (String root : bopRootJobs) {
            data.writeUtf(root);
        }
    }

    public static int read(FriendlyByteBuf buf) {
        try {
            FlagInfo.read(buf); // consumed but FlagInfo reconstructed separately
            int count = buf.readInt();
            for (int i = 0; i < count; i++) {
                buf.readUtf();
            }
            return count;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to read bops", e);
            throw e;
        }
    }

    public static ReadResult readWithFlagInfo(FriendlyByteBuf buf) {
        FlagInfo fi = FlagInfo.read(buf);
        int bop = buf.readInt();
        ImmutableList.Builder<String> roots =
                ImmutableList.builder();
        for (int i = 0; i < bop; i++) {
            roots.add(buf.readUtf());
        }
        return new ReadResult(fi, bop, roots.build());
    }

    public record ReadResult(FlagInfo flagInfo, int blocksOfProgress, ImmutableList<String> bopRootJobs) {
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
