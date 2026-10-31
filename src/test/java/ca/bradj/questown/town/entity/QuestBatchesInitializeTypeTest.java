package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatches;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * World-load reconstruction ({@code QuestBatches.initialize} ->
 * {@code filterOutDuplicateCompletion}) must preserve the QUEST TYPE. The
 * FIRST_WORK_REQUEST and CONCURRENT_JOBS cases used to route through the generic
 * {@code addNewQuest}, silently downgrading them to ROOM-typed recipe quests: raw-id card
 * name, "No recipe found" warn, and a completion check that skips them. User playtest,
 * 2026-10-14: the day-2 quest card showed "special_quest.first_work_request".
 */
class QuestBatchesInitializeTypeTest {

    private static final ResourceLocation JOB_A = JobID.toRL(new JobID("farmer", "wheat"));
    private static final ResourceLocation JOB_B = JobID.toRL(new JobID("baker", "bread"));

    private static final QuestBatches.VillagerProvider<ca.bradj.roomrecipes.serialization.MCRoom>
            NO_VILLAGERS =
            new QuestBatches.VillagerProvider<>() {
                @Override
                public UUID getRandomVillager() {
                    return null;
                }

                @Override
                public boolean isVillagerMissing(UUID uuid) {
                    return true;
                }
            };

    @Test
    void firstWorkRequestQuestKeepsItsTypeThroughInitialize() {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.firstWorkRequest(batchUUID, null));
        MCQuestBatch batch = inputs.withRewardUponCompletion(new NoOpReward());

        TownQuests town = new TownQuests();
        town.questBatches.initialize(NO_VILLAGERS, ImmutableList.of(batch));

        MCQuest rebuilt = town.questBatches.getAll().stream()
                .filter(q -> SpecialQuests.FIRST_WORK_REQUEST.equals(q.getWantedId()))
                .findFirst().orElseThrow();
        assertEquals(
                Quest.QuestType.FIRST_WORK_REQUEST,
                rebuilt.getType(),
                "initialize must rebuild with the FIRST_WORK_REQUEST factory, not the generic ROOM one");
    }

    @Test
    void concurrentJobsQuestKeepsTypeAndJobListThroughInitialize() {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.concurrentJobs(batchUUID, ImmutableList.of(JOB_A, JOB_B)));
        MCQuestBatch batch = inputs.withRewardUponCompletion(new NoOpReward());

        TownQuests town = new TownQuests();
        town.questBatches.initialize(NO_VILLAGERS, ImmutableList.of(batch));

        MCQuest rebuilt = town.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.CONCURRENT_JOBS)
                .findFirst().orElseThrow();
        assertEquals(
                ImmutableList.of(JOB_A, JOB_B),
                ImmutableList.copyOf(rebuilt.getConcurrentJobIds()),
                "rebuild must preserve the concurrent job list");
    }
}
