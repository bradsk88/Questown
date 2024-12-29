package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public class TownEconomicsMenu extends AbstractContainerMenu implements FlagTabsEmbedding {

    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenFlagMenuMessage.VILLAGERS,
            OpenFlagMenuMessage.QUESTS
    );
    private BlockPos flagPos;

    public static TownEconomicsMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        FlagMenus menus = FlagMenus.fromNetwork(windowId, inv.player, buf);
        return menus.econMenu;
    }


    public <S extends IStatus<S>> TownEconomicsMenu(
            int windowId,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.TOWN_ECONOMICS.get(), windowId);
        this.flagPos = flagPos;
    }

    public static VillagerEconomicsData read(FriendlyByteBuf buf) {
        return new VillagerEconomicsData(
                ImmutableList.of()
        );
    }

    public static void write(
            VillagerEconomicsData data,
            FriendlyByteBuf buf
    ) {
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
    public BlockPos getFlagPos() {
        return flagPos;
    }
}
