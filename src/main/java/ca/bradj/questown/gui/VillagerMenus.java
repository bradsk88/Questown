package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
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
    VillagerEconomicsMenu econMenu;
    VillagerBlockofProgressMenu bopMenu;

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
        JobID jobId = Jobs.getIdFromNetwork(buf);

        int invSize = buf.readInt();
        Collection<UIQuest> quests = VillagerQuestsContainer.readQuests(buf);
        BlockPos flagPos = VillagerQuestsContainer.readFlagPos(buf);
        VillagerStatsData stats = VillagerStatsMenu.read(buf);
        VillagerEconomicsData econ = VillagerEconomicsMenu.read(buf);
        boolean showBlockOfProgressTab = buf.readBoolean();

        // TODO[Performance]: Rather than getting the entity, get the uuid and slot locks
        VisitorMobEntity e = (VisitorMobEntity) player.level.getEntity(i);
        VillagerMenus menus = new VillagerMenus(e);
        // Never provide these initializers with the entity, itself. Instead, pass the entity's UUID.
        // It tends to cause client-side-only bugs that don't show up in the dev environment.
        menus.initQuestsMenu(windowId, e.getUUID(), quests, flagPos, showBlockOfProgressTab);
        menus.initVillagerStatsMenu(windowId, flagPos, stats, showBlockOfProgressTab);
        menus.initInventory(
                windowId,
                jobId,
                player,
                e.getUUID(),
                e.getSlotLocks(),
                invSize,
                flagPos,
                showBlockOfProgressTab
        );
        menus.initVillagerEconomicsMenu(windowId, flagPos, econ, showBlockOfProgressTab);
        menus.bopMenu = new VillagerBlockofProgressMenu(windowId, e.getUUID(), flagPos);
        return menus;
    }

    public static void write(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            VisitorMobEntity e,
            int capacity,
            JobID jobId,
            VillagerStatsData stats,
            VillagerEconomicsData econ,
            boolean showBlockOfProgressTab
    ) {
        data.writeInt(e.getId());
        data.writeUtf(jobId.rootId());
        data.writeUtf(jobId.jobId());
        data.writeInt(capacity);
        VillagerQuestsContainer.write(data, quests, e.getFlagPos());
        VillagerStatsMenu.write(stats, data);
        VillagerEconomicsMenu.write(econ, data);
        data.writeBoolean(showBlockOfProgressTab);
    }

    private InventoryAndStatusMenu initInventory(
            int windowId,
            JobID jobId,
            Player player,
            UUID uuid,
            Collection<Boolean> slotLocks,
            int invSize,
            BlockPos flagPos,
            boolean showBlockOfProgressTab
    ) {
        invMenu = new InventoryAndStatusMenu(
                windowId,
                // Minecraft will handle filling this container by syncing from server
                new SimpleContainer(invSize) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                }, player.getInventory(), slotLocks, uuid, jobId, flagPos, showBlockOfProgressTab
        );
        return invMenu;
    }

    public VillagerQuestsContainer initQuestsMenu(
            int windowId,
            UUID uuid,
            Collection<UIQuest> quests,
            BlockPos flagPos,
            boolean showBlockOfProgressTab
    ) {
        questsMenu = new VillagerQuestsContainer(windowId, uuid, quests, flagPos, showBlockOfProgressTab);
        return questsMenu;
    }

    public VillagerStatsMenu initVillagerStatsMenu(
            int windowId,
            BlockPos flagPos,
            VillagerStatsData data,
            boolean showBlockOfProgressTab
    ) {
        statsMenu = new VillagerStatsMenu(windowId, this.entity, flagPos, data, showBlockOfProgressTab);
        return statsMenu;
    }

    public VillagerEconomicsMenu initVillagerEconomicsMenu(
            int windowId,
            BlockPos flagPos,
            VillagerEconomicsData data,
            boolean showBlockOfProgressTab
    ) {
        econMenu = new VillagerEconomicsMenu(windowId, this.entity, flagPos, data, showBlockOfProgressTab);
        return econMenu;
    }
}
