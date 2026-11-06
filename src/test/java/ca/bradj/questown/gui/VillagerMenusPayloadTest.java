package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.VillagerStatsData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The menu's network data stands alone; the entity id is only used when the entity is still there.
class VillagerMenusPayloadTest {

    private static final UUID VILLAGER = UUID.fromString("0e5f0a3c-8a18-4f2e-9d41-1b6c0d12e5aa");

    private static VillagerMenusPayload sample() {
        return new VillagerMenusPayload(
                1234,
                VILLAGER,
                new JobID("gatherer", "downtime"),
                9,
                List.of(),
                new BlockPos(1, 64, -2),
                new VillagerStatsData(0.75f, 3, 100, 0.8f, 0.1f, 2, com.google.common.collect.ImmutableList.of("questown:sleepless")),
                VillagerEconomicsData.none(),
                true,
                true,
                false,
                false,
                List.of(true, false, true, false, false, false, false, false),
                "Villager 2",
                true,
                "hunter",
                false,
                3
        );
    }

    @Test
    void payloadRoundTripsThroughNetwork() {
        VillagerMenusPayload p = sample();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        p.write(buf);
        VillagerMenusPayload r = VillagerMenusPayload.read(buf);

        assertEquals(p.entityId, r.entityId);
        assertEquals(VILLAGER, r.villager);
        assertEquals("gatherer", r.jobId.rootId());
        assertEquals("downtime", r.jobId.jobId());
        assertEquals(9, r.invSize);
        assertTrue(r.quests.isEmpty());
        assertEquals(new BlockPos(1, 64, -2), r.flagPos);
        assertEquals(0.75f, r.stats.fullnessPercent());
        assertEquals(3, r.stats.experienceValue());
        assertEquals(100, r.stats.experienceTarget());
        assertEquals(0.8f, r.stats.moodPercent());
        assertEquals(0.1f, r.stats.damageLevelPercent());
        assertEquals(2, r.stats.level());
        assertEquals(List.of("questown:sleepless"), r.stats.moodEffects());
        assertEquals("hunter", r.promisedRoot);
        assertEquals(false, r.showChangeRootTab);
        assertEquals(3, r.flagBops);
        assertEquals(p.showBlockOfProgressTab, r.showBlockOfProgressTab);
        assertEquals(p.showSkillsTab, r.showSkillsTab);
        assertEquals(false, r.showInventoryTab);
        assertEquals("Villager 2", r.villagerDisplayName);
        assertEquals(p.jobChangeAlreadyPending, r.jobChangeAlreadyPending);
        assertEquals(List.of(true, false, true, false, false, false, false, false), r.slotLocks);
        assertTrue(r.changeRootAttention, "the change-profession red-dot flag survives the wire");
        assertEquals(0, buf.readableBytes(), "payload must consume exactly what it wrote");
    }
}
