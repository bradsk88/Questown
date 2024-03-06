package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.VillagerStatsData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class VillagerMenus {
    final VisitorMobEntity entity;
    InventoryAndStatusMenu invMenu;
    VillagerStatsMenu statsMenu;
    VillagerQuestsContainer questsMenu;

    public VillagerMenus(VisitorMobEntity e) {
        this.entity = e;
    }

    public static VillagerMenus fromNetwork(
            int windowId,
            Player player,
            FriendlyByteBuf buf
    ) {
        // Buffer reads - order must match write()
        int i = buf.readInt();
        String rootId = buf.readUtf();
        String jobId1 = buf.readUtf();
        int invSize = buf.readInt();
        Collection<UIQuest> quests = VillagerQuestsContainer.readQuests(buf);
        BlockPos flagPos = VillagerQuestsContainer.readFlagPos(buf);
        VillagerStatsData stats = VillagerStatsMenu.read(buf);

        JobID jobId = new JobID(rootId, jobId1);
        VisitorMobEntity e = (VisitorMobEntity) player.level.getEntity(i);
        VillagerMenus menus = new VillagerMenus(e);
        menus.initQuestsMenu(windowId, e.getUUID(), quests, flagPos);

        menus.initVillagerStatsMenu(windowId, flagPos, stats);
        menus.initInventory(windowId, jobId, player, e, invSize, flagPos);
        return menus;
    }

    public static void write(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            VisitorMobEntity e,
            int capacity,
            JobID jobId,
            VillagerStatsData stats
    ) {
        data.writeInt(e.getId());
        data.writeUtf(jobId.rootId());
        data.writeUtf(jobId.jobId());
        data.writeInt(capacity);
        VillagerQuestsContainer.write(data, quests, e.getFlagPos());
        VillagerStatsMenu.write(stats, data);
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

    public VillagerQuestsContainer initQuestsMenu(
            int windowId, UUID uuid, Collection<UIQuest> quests, BlockPos flagPos
    ) {
        questsMenu = new VillagerQuestsContainer(windowId, uuid, quests, flagPos);
        return questsMenu;
    }

    public VillagerStatsMenu initVillagerStatsMenu(
            int windowId,
            BlockPos flagPos,
            VillagerStatsData data
    ) {
        statsMenu = new VillagerStatsMenu(windowId, this.entity, flagPos, data);
        return statsMenu;
    }
}
