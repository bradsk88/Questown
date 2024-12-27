package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Stack;

public class VillagerEconomicsMenu extends AbstractVillagerMenu implements VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenVillagerMenuMessage.INVENTORY,
            OpenVillagerMenuMessage.STATS,
            OpenVillagerMenuMessage.QUESTS,
            OpenVillagerMenuMessage.SKILLS
    );
    private final Stack<Runnable> closers = new Stack<>();

    public static VillagerEconomicsMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.econMenu;
    }


    public <S extends IStatus<S>> VillagerEconomicsMenu(
            int windowId,
            VisitorMobEntity entity,
            BlockPos flagPos,
            VillagerEconomicsData initialData
    ) {
        super(MenuTypesInit.VILLAGER_ECONOMICS.get(), windowId, flagPos, entity.getUUID());
    }

    public static VillagerEconomicsData read(FriendlyByteBuf buf) {
        return  new VillagerEconomicsData(
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
}
