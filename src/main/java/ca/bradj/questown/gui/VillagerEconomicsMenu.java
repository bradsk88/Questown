package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

public class VillagerEconomicsMenu extends AbstractTabbedVillagerMenu implements VillagerTabsEmbedding {
    // Every tab but economics, the open one.
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.ECONOMICS);
    private final boolean showBlockOfProgressTab;
    private final boolean showSkillsTab;
    private final boolean showInventoryTab;

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
            UUID villager,
            BlockPos flagPos,
            VillagerEconomicsData initialData,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab
    ) {
        super(MenuTypesInit.VILLAGER_ECONOMICS.get(), null, null, windowId, flagPos, villager);
        this.showBlockOfProgressTab = showBlockOfProgressTab;
        this.showSkillsTab = showSkillsTab;
        this.showInventoryTab = showInventoryTab;
    }

    // Economics data arrives in its own EconomicsUpdate message, so this menu has no codec.

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int i
    ) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onClose() {
        // Nothing
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    @Override
    public Collection<String> getEnabledTabs() {
        Collection<String> tabs = showSkillsTab ? ENABLED_TABS : VillagerTabs.hideSkills(ENABLED_TABS);
        return showInventoryTab ? tabs : VillagerTabs.hideInventory(tabs);
    }

    @Override
    public boolean showInventoryTab() {
        return showInventoryTab;
    }

    @Override
    public boolean showSkillsTab() {
        return showSkillsTab;
    }

    @Override
    public boolean showBlockOfProgressTab() {
        return showBlockOfProgressTab;
    }
}
