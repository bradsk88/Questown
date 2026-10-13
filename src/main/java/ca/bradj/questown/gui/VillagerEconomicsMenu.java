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
    // Every tab except ECONOMICS itself (the pane that is open). The old hand-rolled
    // list omitted CHANGE_ROOT, so with the economics pane open the "switch job" tab's
    // open-fn was null and it rendered as selected alongside the economics tab
    // (2026-10-14 playtest).
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.ECONOMICS);
    private final boolean showBlockOfProgressTab;
    private final boolean showSkillsTab;

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
            boolean showSkillsTab
    ) {
        super(MenuTypesInit.VILLAGER_ECONOMICS.get(), null, null, windowId, flagPos, villager);
        this.showBlockOfProgressTab = showBlockOfProgressTab;
        this.showSkillsTab = showSkillsTab;
    }

    // NOTE: economics data is NOT part of the villager-menu wire — it flows
    // via the separate `EconomicsUpdate` message sent before openScreen.
    // There is deliberately no read/write codec here, and there must not be
    // one added: `VillagerMenusPayload` is the single wire source of truth.

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
        return showSkillsTab ? ENABLED_TABS : VillagerTabs.hideSkills(ENABLED_TABS);
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
