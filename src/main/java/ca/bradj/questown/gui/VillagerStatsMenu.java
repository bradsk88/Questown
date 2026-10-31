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
import net.minecraft.world.inventory.DataSlot;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Stack;
import java.util.UUID;
import java.util.function.Consumer;

public class VillagerStatsMenu extends AbstractTabbedVillagerMenu implements Consumer<VillagerStatsData>,
        VillagerTabsEmbedding {
    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.STATS);
    private final DataSlot fullnessSlot;
    private final DataSlot damageSlot;
    private final DataSlot moodSlot;
    private final DataSlot experienceSlot;
    private final DataSlot experienceTargetSlot;
    private final DataSlot levelSlot;
    private ImmutableList<String> moodEffects;
    private final Stack<Runnable> closers = new Stack<>();
    private final boolean showBlockOfProgressTab;
    private final boolean showSkillsTab;
    private final boolean showInventoryTab;

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
            UUID villager,
            BlockPos flagPos,
            VillagerStatsData initialData,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab
    ) {
        super(MenuTypesInit.VILLAGER_STATS.get(), null, null, windowId, flagPos, villager);
        this.showBlockOfProgressTab = showBlockOfProgressTab;
        this.showSkillsTab = showSkillsTab;
        this.showInventoryTab = showInventoryTab;

        this.addDataSlot(this.fullnessSlot = DataSlot.standalone());
        this.fullnessSlot.set((int) (initialData.fullnessPercent() * 100));

        this.addDataSlot(this.experienceSlot = DataSlot.standalone());
        this.experienceSlot.set(initialData.experienceValue());
        this.addDataSlot(this.experienceTargetSlot = DataSlot.standalone());
        this.experienceTargetSlot.set(initialData.experienceTarget());

        this.addDataSlot(this.moodSlot = DataSlot.standalone());
        this.moodSlot.set((int) (initialData.moodPercent() * 100));

        this.addDataSlot(this.damageSlot = DataSlot.standalone());
        this.damageSlot.set((int) (initialData.damageLevelPercent() * 100));

        this.addDataSlot(this.levelSlot = DataSlot.standalone());
        this.levelSlot.set(initialData.level());
        this.moodEffects = initialData.moodEffects();
    }

    /**
     * Subscribe this menu to the townie's live stats updates; degrades to the
     * static wire data when the local entity is unavailable.
     */
    public void attach(VisitorMobEntity e) {
        e.addStatsListener(this);
        this.closers.add(() -> e.removeStatsListener(this));
    }

    public static VillagerStatsData read(FriendlyByteBuf buf) {
        return new VillagerStatsData(
                buf.readFloat(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), ImmutableList.copyOf(buf.readList(FriendlyByteBuf::readUtf))
        );
    }

    public static void write(
            VillagerStatsData data,
            FriendlyByteBuf buf
    ) {
        buf.writeFloat(data.fullnessPercent());
        buf.writeInt(data.experienceValue());
        buf.writeInt(data.experienceTarget());
        buf.writeFloat(data.moodPercent());
        buf.writeFloat(data.damageLevelPercent());
        buf.writeVarInt(data.level());
        buf.writeCollection(data.moodEffects(), FriendlyByteBuf::writeUtf);
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
        this.levelSlot.set(villagerStatsData.level());
        this.moodEffects = villagerStatsData.moodEffects();
    }

    public int getLevel() {
        return levelSlot.get();
    }

    public ImmutableList<String> getMoodEffects() {
        return moodEffects;
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

    public int getExperienceValue() {
        return experienceSlot.get();
    }

    public int getExperienceTarget() {
        return experienceTargetSlot.get();
    }
}
