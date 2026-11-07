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

/** The villager menus' network data. The client builds the menus from it, not from the local entity, which may have despawned. */
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
    /** The villager's "Villager N" name; empty when it has none. */
    public final String villagerDisplayName;
    /** Red dot on the change-job tab. */
    public final boolean changeRootAttention;
    // The job a Block of Progress spend is promised to give, or "".
    public final String promisedRoot;
    /** False while onboarding hides the change-job tab from this villager. */
    public final boolean showChangeRootTab;
    /** Opened from the flag: the flag's Block of Progress count, spent instead of the player's. Zero otherwise. */
    public final int flagBops;

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
            boolean changeRootAttention,
            String promisedRoot,
            boolean showChangeRootTab,
            int flagBops
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
        this.promisedRoot = promisedRoot;
        this.showChangeRootTab = showChangeRootTab;
        this.flagBops = flagBops;
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
                // Null is sent as the BROKEN quest.
                buf.writeResourceLocation(q == null ? SpecialQuests.BROKEN : q.getWantedId());
                ser.toNetwork(buf, q);
            }
        }
        buf.writeInt(flagPos.getX());
        buf.writeInt(flagPos.getY());
        buf.writeInt(flagPos.getZ());
        VillagerStatsMenu.write(stats, buf);
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
        buf.writeUtf(promisedRoot);
        buf.writeBoolean(showChangeRootTab);
        buf.writeInt(flagBops);
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
        // Economics data is sent separately.
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
        String promisedRoot = buf.readUtf();
        boolean showChangeRootTab = buf.readBoolean();
        int flagBops = buf.readInt();
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
                changeRootAttention,
                promisedRoot,
                showChangeRootTab,
                flagBops
        );
    }
}
