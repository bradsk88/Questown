package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.questown.town.quests.VillagerLevelQuests;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "train a villager" quest (dev 2026-10-27): a curated path posts "Train a Crafter to level 3"
 * ahead of a batch whose skills need level 3, so the batch opens only once a crafter can learn them.
 * It completes once any villager working the root reaches the level.
 */
class TownQuestsVillagerLevelTest {

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        }

        @Override
        public void questLost(MCQuest quest) {
        }
    };

    private static TownQuests townWithLevelQuest(String root, int level) {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.villagerLevel(batchUUID, null, root, level));
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));
        return quests;
    }

    private static MCQuest levelQuest(TownQuests quests) {
        return quests.questBatches.getAll().stream()
                                  .filter(q -> q.getType() == Quest.QuestType.VILLAGER_LEVEL)
                                  .findFirst()
                                  .orElseThrow();
    }

    @Test
    void aCrafterAtTheLevel_completesTheQuest() {
        TownQuests quests = townWithLevelQuest("crafter", 3);

        quests.markVillagerLevelsReached(Map.of("crafter", 3));

        assertTrue(levelQuest(quests).isComplete());
    }

    @Test
    void aCrafterBelowTheLevel_leavesItOpen() {
        TownQuests quests = townWithLevelQuest("crafter", 3);

        quests.markVillagerLevelsReached(Map.of("crafter", 2));

        assertFalse(levelQuest(quests).isComplete());
    }

    @Test
    void anotherRootAtTheLevel_doesNotCount() {
        TownQuests quests = townWithLevelQuest("crafter", 3);

        quests.markVillagerLevelsReached(Map.of("gatherer", 7));

        assertFalse(levelQuest(quests).isComplete());
    }

    @Test
    void theIdCarriesRootAndLevel() {
        MCQuest q = MCQuest.villagerLevel(UUID.randomUUID(), null, "crafter", 5);

        assertEquals(Optional.of(new VillagerLevelQuests.Target("crafter", 5)), VillagerLevelQuests.target(q.getWantedId()));
        assertEquals(Optional.empty(), VillagerLevelQuests.target(MCQuest.skillUnlock(UUID.randomUUID(), null, "cook").getWantedId()));
    }
}
