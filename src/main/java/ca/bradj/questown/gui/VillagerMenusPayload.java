package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.VillagerStatsData;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The villager-menu wire payload. Carries the villager's UUID and slot locks
 * so the client builds the menus from wire data alone instead of resolving
 * the local entity by raw id — a local entity that despawned in the interim
 * used to NPE the screen open and disconnect the client (see
 * .scratch/work-board-request/quests-menu-null-townie-client-crash.md).
 *
 * {@code entityId} is best-effort only: it lets the client attach live
 * stats updates when the local entity happens to still be present, and is
 * ignored (degrading to the wire's static data) when it is not.
 */
public final class VillagerMenusPayload {
    public final int entityId;
    public final UUID villager;
    public final JobID jobId;
    public final int invSize;
    public final Collection<UIQuest> quests;
    public final BlockPos flagPos;
    public final VillagerStatsData stats;
    public final VillagerEconomicsData econ;
    public final boolean showBlockOfProgressTab;
    public final boolean showSkillsTab;
    public final boolean showInventoryTab;
    public final boolean jobChangeAlreadyPending;
    public final List<Boolean> slotLocks;
    /**
     * "Villager N" display name (the townie's monotonic arrival number, 2026-10-14
     * playtest). Empty string when unassigned - screens fall back to the truncated UUID.
     */
    public final String villagerDisplayName;
    /** Red dot on the change-profession tab (TownQuests.wantsJobChangeFrom, server-computed). */
    public final boolean changeRootAttention;

    public VillagerMenusPayload(
            int entityId,
            UUID villager,
            JobID jobId,
            int invSize,
            Collection<UIQuest> quests,
            BlockPos flagPos,
            VillagerStatsData stats,
            VillagerEconomicsData econ,
            boolean showBlockOfProgressTab,
            boolean showSkillsTab,
            boolean showInventoryTab,
            boolean jobChangeAlreadyPending,
            List<Boolean> slotLocks,
            String villagerDisplayName,
            boolean changeRootAttention
    ) {
        this.entityId = entityId;
        this.villager = villager;
        this.jobId = jobId;
        this.invSize = invSize;
        this.quests = quests;
        this.flagPos = flagPos;
        this.stats = stats;
        this.econ = econ;
        this.showBlockOfProgressTab = showBlockOfProgressTab;
        this.showSkillsTab = showSkillsTab;
        this.showInventoryTab = showInventoryTab;
        this.jobChangeAlreadyPending = jobChangeAlreadyPending;
        this.slotLocks = slotLocks;
        this.villagerDisplayName = villagerDisplayName == null ? "" : villagerDisplayName;
        this.changeRootAttention = changeRootAttention;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUUID(villager);
        buf.writeUtf(jobId.rootId());
        buf.writeUtf(jobId.jobId());
        buf.writeInt(invSize);
        int questCount = quests.size();
        buf.writeInt(questCount);
        if (questCount > 0) {
            UIQuest.Serializer ser = new UIQuest.Serializer();
            for (UIQuest q : quests) {
                // Mirrors AbstractQuestsContainer.writeQuests: null is a sentinel
                // rendered as the BROKEN quest.
                buf.writeResourceLocation(q == null ? SpecialQuests.BROKEN : q.getWantedId());
                ser.toNetwork(buf, q);
            }
        }
        buf.writeInt(flagPos.getX());
        buf.writeInt(flagPos.getY());
        buf.writeInt(flagPos.getZ());
        VillagerStatsMenu.write(stats, buf);
        // econ is passed through, not serialized (see note on VillagerEconomicsMenu)
        buf.writeBoolean(showBlockOfProgressTab);
        buf.writeBoolean(showSkillsTab);
        buf.writeBoolean(showInventoryTab);
        buf.writeBoolean(jobChangeAlreadyPending);
        buf.writeVarInt(slotLocks.size());
        for (Boolean locked : slotLocks) {
            buf.writeBoolean(locked);
        }
        buf.writeUtf(villagerDisplayName);
        buf.writeBoolean(changeRootAttention);
    }

    public static VillagerMenusPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        UUID villager = buf.readUUID();
        JobID jobId = new JobID(buf.readUtf(), buf.readUtf());
        int invSize = buf.readInt();
        int questCount = buf.readInt();
        Collection<UIQuest> quests;
        if (questCount == 0) {
            quests = ImmutableList.of();
        } else {
            // Mirrors AbstractQuestsContainer.readQuests (decode + sort).
            quests = buf.readCollection(
                    c -> new ArrayList<>(questCount), b -> {
                        ResourceLocation id = b.readResourceLocation();
                        return new UIQuest.Serializer().fromNetwork(id, b);
                    }
            );
            ((List<UIQuest>) quests).sort(UIQuest::compareTo);
        }
        BlockPos flagPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        VillagerStatsData stats = VillagerStatsMenu.read(buf);
        // econ is passed through, not serialized: economics data flows via the
        // separate `EconomicsUpdate` message, so decoded payloads always carry none().
        VillagerEconomicsData econ = VillagerEconomicsData.none();
        boolean showBlockOfProgressTab = buf.readBoolean();
        boolean showSkillsTab = buf.readBoolean();
        boolean showInventoryTab = buf.readBoolean();
        boolean jobChangeAlreadyPending = buf.readBoolean();
        int lockCount = buf.readVarInt();
        List<Boolean> slotLocks = new ArrayList<>(lockCount);
        for (int i = 0; i < lockCount; i++) {
            slotLocks.add(buf.readBoolean());
        }
        String villagerDisplayName = buf.readUtf();
        boolean changeRootAttention = buf.readBoolean();
        return new VillagerMenusPayload(
                entityId,
                villager,
                jobId,
                invSize,
                quests,
                flagPos,
                stats,
                econ,
                showBlockOfProgressTab,
                showSkillsTab,
                showInventoryTab,
                jobChangeAlreadyPending,
                slotLocks,
                villagerDisplayName,
                changeRootAttention
        );
    }
}
