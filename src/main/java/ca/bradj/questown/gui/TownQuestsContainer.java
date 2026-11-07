package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TownQuestsContainer extends AbstractQuestsContainer implements FlagTabsEmbedding {


    private static final Collection<String> ENABLED_TABS = FlagTabs.allExcept(OpenFlagMenuMessage.QUESTS);

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            FlagInfo flag,
            Runnable triggerAdvancement
    ) {
        this(windowId, quests, flag, summary, triggerAdvancement, Map.of());
    }

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            FlagInfo flag,
            QuestScreenSummary summary,
            Runnable triggerAdvancement,
            Map<UUID, String> villagerDisplayNames
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId, quests, flag, villagerDisplayNames);
        this.summary = summary;
        triggerAdvancement.run();
    }

    public static TownQuestsContainer ForClient(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        FlagMenus menus = FlagMenus.fromNetwork(windowId, inv.player, data);
        return menus.questsMenu;
    }

    public static void write(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            BlockPos pos
    ) {
        writeQuests(data, quests);
        writeFlagPos(data, pos);
        writeVillagerNames(data, villagerDisplayNames);
    }

    @Override
    public Collection<String> getEnabledTabs() {
        return ENABLED_TABS;
    }

    @Override
    public FlagInfo getFlagInfo() {
        return flagInfo;
    }
}