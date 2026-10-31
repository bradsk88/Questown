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

public class VillagerBlockofProgressMenu extends AbstractTabbedVillagerMenu implements VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.BOP);
    private final boolean showSkillsTab;
    private final boolean showInventoryTab;
    /** "Villager N" display name for the screen text; empty = fall back to the UUID. */
    public final String villagerDisplayName;

    public static VillagerBlockofProgressMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.bopMenu;
    }


    public <S extends IStatus<S>> VillagerBlockofProgressMenu(
            int windowId,
            UUID entity,
            BlockPos flagPos,
            boolean showSkillsTab,
            boolean showInventoryTab,
            String villagerDisplayName
    ) {
        super(MenuTypesInit.VILLAGER_BLOCKS_OF_PROGRESS.get(), null, null, windowId, flagPos, entity);
        this.showSkillsTab = showSkillsTab;
        this.showInventoryTab = showInventoryTab;
        this.villagerDisplayName = villagerDisplayName == null ? "" : villagerDisplayName;
    }

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
        return true;
    }
}
