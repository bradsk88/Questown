package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.VillagerStatsData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class VillagerMenus {
    InventoryAndStatusMenu invMenu;
    VillagerStatsMenu statsMenu;
    VillagerQuestsContainer questsMenu;
    VillagerEconomicsMenu econMenu;
    VillagerBlockofProgressMenu bopMenu;
    JobChangeConfirmMenu changeMenu;

    public static VillagerMenus fromNetwork(
            int windowId,
            Player player,
            FriendlyByteBuf buf
    ) {
        // Buffer reads - order must match write()
        try {
            return doFromNetwork(windowId, player, buf);
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to load menus from network", e);
            throw e;
        }
    }

    private static @NotNull VillagerMenus doFromNetwork(
            int windowId,
            Player player,
            FriendlyByteBuf buf
    ) {
        VillagerMenusPayload p = VillagerMenusPayload.read(buf);
        VillagerMenus menus = new VillagerMenus();
        menus.initQuestsMenu(windowId, p.villager, p.quests, p.flagPos, p.showBlockOfProgressTab, p.showSkillsTab, p.showInventoryTab);
        VillagerStatsMenu statsMenu =
                new VillagerStatsMenu(windowId, p.villager, p.flagPos, p.stats, p.showBlockOfProgressTab, p.showSkillsTab, p.showInventoryTab);
        // Best-effort: the local entity may have despawned since the server
        // sent this. Without it the menu degrades to the wire's static data.
        if (player.level().getEntity(p.entityId) instanceof VisitorMobEntity local) {
            statsMenu.attach(local);
        }
        menus.statsMenu = statsMenu;
        menus.initInventory(
                windowId,
                p.jobId,
                player,
                p.villager,
                p.slotLocks,
                p.invSize,
                p.flagPos,
                p.showBlockOfProgressTab,
                p.showSkillsTab,
                p.showInventoryTab
        );
        menus.initVillagerEconomicsMenu(windowId, p.villager, p.flagPos, p.econ, p.showBlockOfProgressTab, p.showSkillsTab, p.showInventoryTab);
        menus.bopMenu = new VillagerBlockofProgressMenu(windowId, p.villager, p.flagPos, p.showSkillsTab, p.showInventoryTab, p.villagerDisplayName);

        SimpleContainer bopSlot = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };

        menus.changeMenu = new JobChangeConfirmMenu(
                windowId,
                bopSlot,
                player.getInventory(),
                p.villager,
                p.jobId,
                p.flagPos,
                p.jobChangeAlreadyPending,
                p.showSkillsTab,
                p.showInventoryTab
        );
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
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab,
            boolean jobChangeAlreadyPending,
            String villagerDisplayName
    ) {
        new VillagerMenusPayload(
                e.getId(),
                e.getUUID(),
                jobId,
                capacity,
                quests,
                e.getFlagPos(),
                stats,
                econ,
                showBlockOfProgressTab,
                showSkillsTab,
                showInventoryTab,
                jobChangeAlreadyPending,
                e.getSlotLocks(),
                villagerDisplayName == null ? "" : villagerDisplayName
        ).write(data);
    }

    private InventoryAndStatusMenu initInventory(
            int windowId,
            JobID jobId,
            Player player,
            UUID uuid,
            Collection<Boolean> slotLocks,
            int invSize,
            BlockPos flagPos,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab
    ) {
        invMenu = new InventoryAndStatusMenu(
                windowId,
                // Minecraft will handle filling this container by syncing from server
                new SimpleContainer(invSize) {
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                }, player.getInventory(), slotLocks, uuid, jobId, flagPos, showBlockOfProgressTab, showSkillsTab, showInventoryTab
        );
        return invMenu;
    }

    public VillagerQuestsContainer initQuestsMenu(
            int windowId,
            UUID uuid,
            Collection<UIQuest> quests,
            BlockPos flagPos,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab
    ) {
        questsMenu = new VillagerQuestsContainer(windowId, uuid, quests, flagPos, showBlockOfProgressTab, showSkillsTab, showInventoryTab);
        return questsMenu;
    }

    public VillagerEconomicsMenu initVillagerEconomicsMenu(
            int windowId,
            UUID villager,
            BlockPos flagPos,
            VillagerEconomicsData data,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab
    ) {
        econMenu = new VillagerEconomicsMenu(windowId, villager, flagPos, data, showBlockOfProgressTab, showSkillsTab, showInventoryTab);
        return econMenu;
    }
}
