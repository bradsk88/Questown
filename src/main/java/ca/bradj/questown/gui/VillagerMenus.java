package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;

public class VillagerMenus {
    final VisitorMobEntity entity;
    InventoryAndStatusMenu invMenu;
    VillagerStatsMenu statsMenu;
    TownQuestsContainer questsMenu;

    public VillagerMenus(VisitorMobEntity e) {
        this.entity = e;
    }

    public static VillagerMenus fromNetwork(
            int windowId,
            Player player,
            FriendlyByteBuf buf
    ) {
        VisitorMobEntity e = (VisitorMobEntity) player.level.getEntity(buf.readInt());
        VillagerMenus menus = new VillagerMenus(e);
        JobID jobId = new JobID(buf.readUtf(), buf.readUtf());
        int invSize = buf.readInt();
        Collection<UIQuest> quests = TownQuestsContainer.readQuests(buf);
        BlockPos flagPos = TownQuestsContainer.readFlagPos(buf);
        menus.initQuestsMenu(windowId, quests, flagPos);
        menus.initVillagerStatsMenu(windowId, flagPos);
        menus.initInventory(windowId, jobId, player, e, invSize, flagPos);
        return menus;
    }

    public static void write(FriendlyByteBuf data, List<UIQuest> quests, VisitorMobEntity e, int capacity, JobID jobId) {
        data.writeInt(e.getId());
        data.writeUtf(jobId.rootId());
        data.writeUtf(jobId.jobId());
        data.writeInt(capacity);
        TownQuestsContainer.write(data, quests, e.getFlagPos());
    }

    private InventoryAndStatusMenu initInventory(
            int windowId, JobID jobId, Player player,
            VisitorMobEntity e, int invSize, BlockPos flagPos
    ) {
        invMenu = new InventoryAndStatusMenu(windowId,
                // Minecraft will handle filling this container by syncing from server
                new SimpleContainer(invSize) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                }, player.getInventory(), e.getSlotLocks(), e, jobId, flagPos
        );
        return invMenu;
    }

    public TownQuestsContainer initQuestsMenu(int windowId, Collection<UIQuest> quests, BlockPos flagPos) {
        questsMenu = new TownQuestsContainer(windowId, quests, flagPos);
        return questsMenu;
    }

    public VillagerStatsMenu initVillagerStatsMenu(int windowId, BlockPos flagPos) {
        statsMenu = new VillagerStatsMenu(windowId, this.entity, flagPos);
        return statsMenu;
    }
}
