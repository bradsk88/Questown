package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collection;
import java.util.List;

public class TownQuestsContainer extends AbstractQuestsContainer implements FlagTabsEmbedding {


    private static final Collection<String> ENABLED_TABS = ImmutableList.of(
            OpenFlagMenuMessage.VILLAGERS,
            OpenFlagMenuMessage.ECONOMICS
    );

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            FlagInfo flag,
            Runnable triggerAdvancement
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId, quests, flag);
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