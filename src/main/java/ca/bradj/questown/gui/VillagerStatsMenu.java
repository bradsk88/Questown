package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
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
import java.util.function.Consumer;

public class VillagerStatsMenu extends AbstractContainerMenu implements Consumer<VillagerStatsData> {
    private final DataSlot fullnessSlot;
    private final DataSlot moodSlot;
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
            BlockPos flagPos,
            VillagerStatsData initialData
    ) {
        super(MenuTypesInit.VILLAGER_STATS.get(), windowId);

        this.openInvFn = VillagerTabs.makeOpenFn(flagPos, entity.getUUID(), OpenVillagerMenuMessage.INVENTORY);
        this.openQuestsFn = VillagerTabs.makeOpenFn(flagPos, entity.getUUID(), OpenVillagerMenuMessage.QUESTS);

        this.addDataSlot(this.fullnessSlot = DataSlot.standalone());
        this.fullnessSlot.set((int) (initialData.fullnessPercent() * 100));

        this.addDataSlot(this.moodSlot = DataSlot.standalone());
        this.moodSlot.set((int) (initialData.moodPercent() * 100));

        entity.addStatsListener(this);
        this.closers.add(() -> entity.removeStatsListener(this));
    }

    public static VillagerStatsData read(FriendlyByteBuf buf) {
        return new VillagerStatsData(buf.readFloat(), buf.readFloat());
    }

    public static void write(VillagerStatsData data, FriendlyByteBuf buf) {
        buf.writeFloat(data.fullnessPercent());
        buf.writeFloat(data.moodPercent());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int i) {
        return ItemStack.EMPTY;
    }

    public boolean stillValid(Player p_38874_) {
        // TODO: Consider checking distance
        return true;
    }

    public void onClose() {
        closers.forEach(Runnable::run);
    }

    @Override
    public void accept(VillagerStatsData villagerStatsData) {
        int fullness = (int) (100 * villagerStatsData.fullnessPercent());
        this.fullnessSlot.set(fullness);
        int mood = (int) (100 * villagerStatsData.moodPercent());
        this.moodSlot.set(mood);
    }

    public int getFullnessPercent() {
        return fullnessSlot.get();
    }

    public int getMoodPercent() {
        return moodSlot.get();
    }

    public void openInv() {
        this.openInvFn.run();
    }

    public void openQuests() {
        this.openQuestsFn.run();
    }
}
