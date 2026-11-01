package ca.bradj.questown.town.quests;

import ca.bradj.questown.jobs.JobID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillQuestLocksTest {

    private static final ResourceLocation STONE_SHOVEL_QUEST = new ResourceLocation("questown", "skill_unlock/crafter/stone_shovel");
    private static final JobID STONE_SHOVEL = new JobID("crafter", "stone_shovel");

    @Test
    void aSkillAboveEveryVillagerInItsJobIsLocked() {
        Map<ResourceLocation, Integer> locked = SkillQuestLocks.lockedLevels(
                Map.of(STONE_SHOVEL_QUEST, STONE_SHOVEL), s -> 5, Map.of("crafter", 4, "hunter", 9));

        assertEquals(Map.of(STONE_SHOVEL_QUEST, 5), locked);
    }

    @Test
    void itUnlocksOnceAVillagerInTheJobReachesTheLevel() {
        assertEquals(Map.of(), SkillQuestLocks.lockedLevels(
                Map.of(STONE_SHOVEL_QUEST, STONE_SHOVEL), s -> 5, Map.of("crafter", 5)));
    }

    @Test
    void withNoVillagerInTheJobItIsLocked() {
        assertEquals(Map.of(STONE_SHOVEL_QUEST, 5), SkillQuestLocks.lockedLevels(
                Map.of(STONE_SHOVEL_QUEST, STONE_SHOVEL), s -> 5, Map.of()));
    }
}
