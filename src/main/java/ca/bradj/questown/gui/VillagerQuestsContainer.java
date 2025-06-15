package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class VillagerQuestsContainer extends AbstractQuestsContainer implements VillagerTabsEmbedding {

    private static final Collection<String> ENABLED_TABS = VillagerTabs.except(OpenVillagerMenuMessage.QUESTS);
    private final UUID villagerUUID;
    private final boolean showBlockOfProgressTab;

    public VillagerQuestsContainer(
            int windowId,
            UUID villagerUUID,
            Collection<UIQuest> quests,
            BlockPos flagPos,
            boolean showBlockOfProgressTab
    ) {
        super(
                MenuTypesInit.VILLAGER_QUESTS.get(),
                windowId,
                quests,
                new FlagTabsEmbedding.FlagInfo(flagPos, showBlockOfProgressTab)
        );
        this.villagerUUID = villagerUUID;
        this.showBlockOfProgressTab = showBlockOfProgressTab;
    }

    public static VillagerQuestsContainer ForClient(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        try {
            VillagerMenus menus = VillagerMenus.fromNetwork(windowId, inv.player, data);
            return menus.questsMenu;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to open villager quests container: {}", e.getMessage());
            throw e;
        }
    }

    public static void write(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            BlockPos pos
    ) {
        writeQuests(data, quests);
        writeFlagPos(data, pos);
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public BlockPos getFlagPos() {
        return flagInfo.flagPos();
    }

    @Override
    public UUID getVillagerUUID() {
        return villagerUUID;
    }

    @Override
    public boolean showBlockOfProgressTab() {
        return showBlockOfProgressTab;
    }
}