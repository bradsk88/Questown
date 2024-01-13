package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collection;
import java.util.List;

public class TownQuestsContainer extends AbstractQuestsContainer {

    public TownQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.TOWN_QUESTS.get(), windowId, quests, flagPos);
    }

    public static TownQuestsContainer ForClient(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        try {
            Collection<UIQuest> q = readQuests(data);
            BlockPos p = readFlagPos(data);
            return new TownQuestsContainer(windowId, q, p);
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to open town quests container: {}", e.getMessage());
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

}