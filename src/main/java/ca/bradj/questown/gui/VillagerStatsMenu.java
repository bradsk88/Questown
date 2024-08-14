package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.VillagerStatsData;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Stack;
import java.util.function.Consumer;

public class VillagerStatsMenu extends AbstractVillagerMenu implements Consumer<VillagerStatsData>, VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenVillagerMenuMessage.INVENTORY,
            OpenVillagerMenuMessage.QUESTS,
            OpenVillagerMenuMessage.SKILLS
    );
    private final DataSlot fullnessSlot;
    private final DataSlot damageSlot;
    private final DataSlot moodSlot;
    private final Stack<Runnable> closers = new Stack<>();
    private final Runnable openInvFn;

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
        super(MenuTypesInit.VILLAGER_STATS.get(), windowId, flagPos, entity.getUUID());

        this.openInvFn = VillagerTabs.makeOpenFn(flagPos, entity.getUUID(), OpenVillagerMenuMessage.INVENTORY);

        this.addDataSlot(this.fullnessSlot = DataSlot.standalone());
        this.fullnessSlot.set((int) (initialData.fullnessPercent() * 100));

        this.addDataSlot(this.moodSlot = DataSlot.standalone());
        this.moodSlot.set((int) (initialData.moodPercent() * 100));

        this.addDataSlot(this.damageSlot = DataSlot.standalone());
        this.damageSlot.set((int) (initialData.damageLevelPercent() * 100));

        entity.addStatsListener(this);
        this.closers.add(() -> entity.removeStatsListener(this));
    }

    public static VillagerStatsData read(FriendlyByteBuf buf) {
        return new VillagerStatsData(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void write(VillagerStatsData data, FriendlyByteBuf buf) {
        buf.writeFloat(data.fullnessPercent());
        buf.writeFloat(data.moodPercent());
        buf.writeFloat(data.damageLevelPercent());
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
        int damage = (int) (100 * villagerStatsData.damageLevelPercent());
        this.damageSlot.set(damage);
        int mood = (int) (100 * villagerStatsData.moodPercent());
        this.moodSlot.set(mood);
    }

    public int getFullnessPercent() {
        return fullnessSlot.get();
    }

    public int getDamageLevel() {
        return damageSlot.get();
    }

    public int getMoodPercent() {
        return moodSlot.get();
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }
}
