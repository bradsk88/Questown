package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

class TownVillagerLearningHandleTest {

    private static final JobID STICK = new JobID("crafter", "stick");
    private static final JobID IRON_HELMET = new JobID("crafter", "iron_helmet");
    private static final JobID WOOD_SHOVEL = new JobID("crafter", "wooden_shovel");
    private static final JobID GOLD_HELMET = new JobID("crafter", "golden_helmet");
    private static final JobID BEEF = new JobID("cook", "beef");

    @Test
    void childJobsKnownToExist_unionsEveryKeyOfTheRoot_acrossVillagers() {
        Map<UUID, Map<JobID, Set<JobID>>> known = Map.of(
                UUID.randomUUID(), Map.of(IRON_HELMET, Set.of(GOLD_HELMET), STICK, Set.of(WOOD_SHOVEL)),
                UUID.randomUUID(), Map.of(new JobID("cook", "extract"), Set.of(BEEF))
        );

        Assertions.assertEquals(
                Set.of(GOLD_HELMET, WOOD_SHOVEL),
                TownVillagerLearningHandle.childJobsKnownToExist(known, new JobID("crafter", "seeking_work"))
        );
    }
}
