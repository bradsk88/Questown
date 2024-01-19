package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.VillagerStatsData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Stack;
import java.util.UUID;
import java.util.function.Consumer;

public class VillagerStatsMenu extends AbstractContainerMenu implements StatusListener, Consumer<VillagerStatsData> {
    private final DataSlot statusSlot;
    private final DataSlot fullnessSlot;
    private final Stack<Runnable> closers = new Stack<>();
    private final Runnable openInvFn;
    private final Runnable openQuestsFn;

    public static VillagerStatsMenu ForClientSide(
            int windowId,
            Inventory inv,
            FriendlyByteBuf buf
    ) {
        VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, buf);
        return menus.statsMenu;
    }


    public <S extends IStatus<S>> VillagerStatsMenu(
            int windowId,
            VisitorMobEntity entity,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.VILLAGER_STATS.get(), windowId);

        this.addDataSlot(this.statusSlot = DataSlot.standalone());
        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(entity.getStatusForServer()));

        this.openInvFn = makeOpenFn(flagPos, entity.getUUID(), OpenVillagerMenuMessage.INVENTORY);
        this.openQuestsFn = makeOpenFn(flagPos, entity.getUUID(), OpenVillagerMenuMessage.QUESTS);

        this.addDataSlot(this.fullnessSlot = DataSlot.standalone());
        this.fullnessSlot.set(88);

        entity.addStatusListener(this);

        entity.addStatsListener(this);
        this.closers.add(() -> entity.removeStatusListener(this));
        this.closers.add(() -> entity.removeStatsListener(this));
    }

    private Runnable makeOpenFn(
            BlockPos fp,
            UUID gathererId,
            String type
    ) {
        Runnable fn = () -> QuestownNetwork.CHANNEL.sendToServer(new OpenVillagerMenuMessage(
                fp.getX(), fp.getY(), fp.getZ(),
                gathererId, type
        ));
        return fn;
    }


    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    public IStatus<?> getStatus() {
        return SessionUniqueOrdinals.getStatus(this.statusSlot.get());
    }

    @Override
    public void statusChanged(IStatus<?> newStatus) {
//        this.statusSlot.set(SessionUniqueOrdinals.getOrdinal(newStatus));
    }

    public void onClose() {
        closers.forEach(Runnable::run);
    }

    @Override
    public void accept(VillagerStatsData villagerStatsData) {
        int fullness = (int) (100 * villagerStatsData.fullnessPercent());
        this.fullnessSlot.set(fullness);
    }

    public int getFullnessPercent() {
        return fullnessSlot.get();
    }

    public void openInv() {
        this.openInvFn.run();
    }

    public void openQuests() {
        this.openQuestsFn.run();
    }
}
