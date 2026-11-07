package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public class JobChangeConfirmMenu extends AbstractTabbedVillagerMenu implements VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.CHANGE_ROOT);

    private static final int boxHeight = 18;
    final JobID jobId;
    final DataSlot alreadyPending;
    private final boolean showInventoryTab;

    final BopTransactionSyncer tx = new BopTransactionSyncer(this::getSlot);

    public static JobChangeConfirmMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.changeMenu;
    }

    private final String promisedRoot;

    public record FlagBop(int count) {
        public static final FlagBop NONE = new FlagBop(0);
    }

    private final FlagBop flagBop;

    public <S extends IStatus<S>> JobChangeConfirmMenu(
            int windowId,
            Container gathererInv,
            Inventory inv,
            UUID villagerUUID,
            JobID jobId,
            BlockPos flagPos,
            boolean alreadyPending
    ) {
        super(MenuTypesInit.CONFIRM_JOB_CHANGE.get(), gathererInv, inv, windowId, flagPos, villagerUUID);
        this.promisedRoot = promisedRoot;
        this.flagBop = flagBop;
        gathererInventoryYOffset = 8;
        this.jobId = jobId;
        this.showInventoryTab = showInventoryTab;
        layoutSlots(gathererInv);
        this.addDataSlot(this.alreadyPending = DataSlot.standalone());
        this.alreadyPending.set(alreadyPending ? 1 : 0);
    }

    @Override
    public void onClose() {
        // Nothing (see "removed")
    }

    @Override
    public void removed(Player p_38940_) {
        super.removed(p_38940_);
        super.clearContainer(p_38940_);
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
        // TODO: Show BOP tab conditionally
        return false;
    }

    /** The job an open onboarding job-change quest promises, if any. */
    public Optional<String> getPromisedRoot() {
        return promisedRoot.isEmpty() ? Optional.empty() : Optional.of(promisedRoot);
    }

    /** True when the slot is empty and the flag has a Block of Progress to spend. */
    public boolean usesFlagBop() {
        return !tx.hasBlockOfProgress() && flagBop.count() > 0;
    }

    public boolean hasBlockOfProgress() {
        return tx.hasBlockOfProgress() || usesFlagBop();
    }

    public int flagBopCount() {
        return flagBop.count();
    }
}
