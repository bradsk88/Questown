package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class JobChangeConfirmMenu extends AbstractTabbedVillagerMenu implements VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.CHANGE_ROOT);

    private static final int boxHeight = 18;
    final JobID jobId;

    public static JobChangeConfirmMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.changeMenu;
    }

    public <S extends IStatus<S>> JobChangeConfirmMenu(
            int windowId,
            Container gathererInv,
            Inventory inv,
            UUID villagerUUID,
            JobID jobId,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.CONFIRM_JOB_CHANGE.get(), gathererInv, inv, windowId, flagPos, villagerUUID);
        gathererInventoryYOffset = 8 + boxHeight + 4;
        this.jobId = jobId;

        layoutSlots(gathererInv);
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

    private void sendItemsBackToPlayerOrLevel(ServerPlayer sp) {
        for (Slot villagerSlot : super.getVillagerSlots()) {
            if (!villagerSlot.hasItem()) {
                continue;
            }
            quickMoveStack(sp, villagerSlot.index);
            if (!villagerSlot.hasItem()) {
                continue;
            }
            ItemEntity ie = new ItemEntity(sp.level, sp.getX(), sp.getY(), sp.getZ(), villagerSlot.getItem());
            sp.level.addFreshEntity(ie);
        }
    }

    public boolean hasBlockOfProgress() {
        // First slot index after four rows of 9.
        int slotIndex = 9 * 4;
        // 35 is the last index of these rows, so 36 is BOP slot.
        return getSlot(slotIndex).getItem().is(ItemsInit.BLOCK_OF_PROGRESS.get());
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public boolean showBlockOfProgressTab() {
        // TODO: Show BOP tab conditionally
        return false;
    }
}
