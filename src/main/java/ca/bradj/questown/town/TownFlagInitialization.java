package ca.bradj.questown.town;

import ca.bradj.questown.town.quests.MCMorningRewards;
import ca.bradj.questown.town.quests.MCQuestBatches;

public interface TownFlagInitialization {
    TownRoomsHandle getRoomsHandle();

    void setUpQuestsForNewlyPlacedFlag();

    void setInitializedQuests(boolean b);

    TownQuestsHandle getQuests();

    MCMorningRewards getMorningRewards();

    TownPois getPOIs();

    TownKnowledgeStore getKnowledge();

    TownVillagerHandle getVillagers();

    TownHealingHandle getHealing();

    MCQuestBatches getQuestBatches();

    TownWorkHandle getWorkHandle();
}
