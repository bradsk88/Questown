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

import static ca.bradj.questown.gui.VillagerTabs.makeOpenFn;

public class VillagerQuestsContainer extends AbstractQuestsContainer {

    private final Runnable openInvFn;
    private final Runnable openStatsFn;

    public VillagerQuestsContainer(
            int windowId,
            UUID uuid,
            Collection<UIQuest> quests,
            BlockPos flagPos
    ) {
        super(MenuTypesInit.VILLAGER_QUESTS.get(), windowId, quests, flagPos);
        this.openInvFn = makeOpenFn(flagPos, uuid, OpenVillagerMenuMessage.INVENTORY);
        this.openStatsFn = makeOpenFn(flagPos, uuid, OpenVillagerMenuMessage.STATS);
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

    public void openInv() {
        this.openInvFn.run();
    }

    public void openStats() {
        this.openStatsFn.run();
    }
}