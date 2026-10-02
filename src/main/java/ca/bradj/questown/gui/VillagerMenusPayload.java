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
    public final boolean jobChangeAlreadyPending;
    public final List<Boolean> slotLocks;

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
            boolean jobChangeAlreadyPending,
            List<Boolean> slotLocks
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
        this.jobChangeAlreadyPending = jobChangeAlreadyPending;
        this.slotLocks = slotLocks;
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
        // VillagerStatsData layout (VillagerStatsMenu.write)
        buf.writeFloat(stats.fullnessPercent());
        buf.writeInt(stats.experienceValue());
        buf.writeInt(stats.experienceTarget());
        buf.writeFloat(stats.moodPercent());
        buf.writeFloat(stats.damageLevelPercent());
        // econ: wire-dead today (VillagerEconomicsMenu.write is a no-op)
        buf.writeBoolean(showBlockOfProgressTab);
        buf.writeBoolean(jobChangeAlreadyPending);
        buf.writeVarInt(slotLocks.size());
        for (Boolean locked : slotLocks) {
            buf.writeBoolean(locked);
        }
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
        VillagerStatsData stats = new VillagerStatsData(
                buf.readFloat(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat()
        );
        // econ is wire-dead (VillagerEconomicsMenu.read returns none())
        VillagerEconomicsData econ = VillagerEconomicsData.none();
        boolean showBlockOfProgressTab = buf.readBoolean();
        boolean jobChangeAlreadyPending = buf.readBoolean();
        int lockCount = buf.readVarInt();
        List<Boolean> slotLocks = new ArrayList<>(lockCount);
        for (int i = 0; i < lockCount; i++) {
            slotLocks.add(buf.readBoolean());
        }
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
                jobChangeAlreadyPending,
                slotLocks
        );
    }
}
