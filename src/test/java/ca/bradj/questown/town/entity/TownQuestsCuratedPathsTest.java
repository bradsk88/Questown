package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.questown.town.quests.paths.QuestPath;
import ca.bradj.questown.town.quests.paths.QuestPathParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The curated-path engine in {@link TownQuests} (plan rulings 2-4, 18-23): starters post at the
 * fork (the onboarding miner batch complete), later batches once their opens_after batches are
 * complete, each once; the ladder holds the garden while a taken path has batches left.
 */
class TownQuestsCuratedPathsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final QuestPath OUTFITTER = QuestPathParser.parse(new Gson().fromJson("""
            {"id": "questown:outfitter", "batches": [
              {"id": "start", "arrival": true, "quests": [
                {"room": "questown:crafting_room"}, {"job_change": "crafter/stick"},
                {"loot": "minecraft:stick", "count": 8}]},
              {"id": "armor", "opens_after": ["start"], "quests": [{"room": "questown:armory"}]},
              {"id": "weapons_1", "opens_after": ["start"], "quests": [{"skill": "crafter/wooden_shovel"}]},
              {"id": "weapons_2", "opens_after": ["weapons_1"], "arrival": true,
               "quests": [{"loot": "minecraft:stone_sword", "count": 1}]}
            ]}""", JsonObject.class));

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {}

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {}

        @Override
        public void questLost(MCQuest quest) {}
    };

    private static TownQuests townWithMinerBeat(boolean minerDone) {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        quests.curatedPaths = () -> List.of(OUTFITTER);
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        MCQuest miner = MCQuest.jobChange(batchUUID, null, TownQuests.ONBOARDING_MINER_JOB);
        miner.setOnboarding(true);
        inputs.addNewQuest(miner);
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));
        if (minerDone) {
            quests.questBatches.markRecipeAsComplete(null, JobID.toRL(TownQuests.ONBOARDING_MINER_JOB));
        }
        return quests;
    }

    private static TestTutorialTownView view() {
        return TestTutorialTownView.builder().villagerCount(4).build();
    }

    private static List<MCQuest> tagged(TownQuests quests, String batch) {
        return quests.questBatches.getAll().stream()
                .filter(q -> q.pathBatch().map(("questown:outfitter/" + batch)::equals).orElse(false))
                .toList();
    }

    private static void completeBatch(TownQuests quests, String batch) {
        for (MCQuest q : tagged(quests, batch)) {
            quests.questBatches.markRecipeAsComplete(null, q.getWantedId());
        }
    }

    @Test
    void beforeTheFork_noPathBatchPosts() {
        TownQuests quests = townWithMinerBeat(false);

        quests.postPathBatchesIfDue(view());

        assertEquals(Set.of(), quests.pathBatchIds(OUTFITTER, false));
    }

    @Test
    void atTheFork_theStarterPostsOnce_onboardingFlagged_andCompletesTheTutorial() {
        TownQuests quests = townWithMinerBeat(true);
        TestTutorialTownView view = view();

        quests.postPathBatchesIfDue(view);
        quests.postPathBatchesIfDue(view);

        assertEquals(Set.of("start"), quests.pathBatchIds(OUTFITTER, false));
        List<MCQuest> start = tagged(quests, "start");
        assertEquals(3, start.size());
        assertTrue(start.stream().allMatch(Quest::isOnboarding), "plan ruling 22: the trunk's job-change treatment");
        assertTrue(start.stream().anyMatch(q -> q.getType() == Quest.QuestType.JOB_CHANGE
                && q.getWantedId().equals(JobID.toRL(new JobID("crafter", "stick")))));
        assertTrue(start.stream().anyMatch(q -> q.getType() == Quest.QuestType.LOOT && q.getCountNeeded() == 8));
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.TutorialComplete));
        assertEquals(List.of(TutorialTownView.PHASE_PATH_ARRIVAL), view.getRewardPhaseNames());
    }

    @Test
    void completingTheStarter_opensBothBranches_thenWeaponsOneOpensWeaponsTwo() {
        TownQuests quests = townWithMinerBeat(true);
        TestTutorialTownView view = view();
        quests.postPathBatchesIfDue(view);

        completeBatch(quests, "start");
        quests.postPathBatchesIfDue(view);
        assertEquals(Set.of("start", "armor", "weapons_1"), quests.pathBatchIds(OUTFITTER, false));

        completeBatch(quests, "weapons_1");
        quests.postPathBatchesIfDue(view);
        assertEquals(Set.of("start", "armor", "weapons_1", "weapons_2"), quests.pathBatchIds(OUTFITTER, false));
        assertEquals(Set.of("start", "weapons_1"), quests.pathBatchIds(OUTFITTER, true));
    }

    /** Progressive discovery reveals a few random children per job; a named skill quest must not hinge on it. */
    @Test
    void postingABatch_revealsItsNamedSkills() {
        TownQuests quests = townWithMinerBeat(true);
        TestTutorialTownView view = view();
        quests.postPathBatchesIfDue(view);
        assertEquals(List.of(), view.getRevealedSkills(), "the starter names no skill");

        completeBatch(quests, "start");
        quests.postPathBatchesIfDue(view);

        assertEquals(List.of(new JobID("crafter", "wooden_shovel")), view.getRevealedSkills());
    }

    /** The ladder's path step: null (go on to the garden) only once a taken path is finished. */
    @Test
    void thePathStep_holdsTheGardenUntilTheTakenPathIsFinished() {
        TownQuests quests = townWithMinerBeat(true);
        TestTutorialTownView view = view();

        assertEquals(TownQuests.Tutorial.SKIPPED, quests.tryCuratedPaths(view), "no path taken yet");

        for (String b : List.of("start", "armor", "weapons_1")) {
            completeBatch(quests, b);
            quests.postPathBatchesIfDue(view);
        }
        assertEquals(TownQuests.Tutorial.SKIPPED, quests.tryCuratedPaths(view), "weapons_2 is still open");

        completeBatch(quests, "weapons_2");
        assertNull(quests.tryCuratedPaths(view));
    }

    /**
     * Agent playtest 2026-10-27 #4: finishing the Outfitter had no moment; it fires an advancement.
     * The per-tick poster sees the end: a path batch's reward queues no ladder pass (held run
     * 2026-10-28, the first cut fired from tryCuratedPaths and never ran).
     */
    @Test
    void finishingThePath_firesItsAdvancementOnce_andNotBefore() {
        TownQuests quests = townWithMinerBeat(true);
        TestTutorialTownView view = view();
        quests.postPathBatchesIfDue(view);
        for (String b : List.of("start", "armor", "weapons_1")) {
            completeBatch(quests, b);
            quests.postPathBatchesIfDue(view);
        }
        assertFalse(view.getTriggersFired().contains(TutorialTrigger.Triggers.OutfitterPath), "weapons_2 is still open");

        completeBatch(quests, "weapons_2");
        quests.postPathBatchesIfDue(view);
        quests.postPathBatchesIfDue(view);

        assertEquals(1, view.getTriggersFired().stream().filter(TutorialTrigger.Triggers.OutfitterPath::equals).count());
    }

    @Test
    void thePathStep_waitsForTheFork() {
        assertEquals(TownQuests.Tutorial.SKIPPED, townWithMinerBeat(false).tryCuratedPaths(view()));
    }

    @Test
    void thePathTagSurvivesNbtAndCompletion() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, new ResourceLocation("questown", "armory"));
        q.setPathBatch("questown:outfitter/armor");

        assertEquals("questown:outfitter/armor",
                MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q)).pathBatch().orElseThrow());
        assertEquals("questown:outfitter/armor", q.completed(null).pathBatch().orElseThrow());
    }
}
