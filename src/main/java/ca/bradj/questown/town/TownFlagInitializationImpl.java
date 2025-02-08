package ca.bradj.questown.town;

import ca.bradj.questown.town.quests.MCMorningRewards;
import ca.bradj.questown.town.quests.MCQuestBatches;

public class TownFlagInitializationImpl implements TownFlagInitialization {
    private final TownFlagBlockEntity flag;

    public TownFlagInitializationImpl(TownFlagBlockEntity flag) {
        this.flag = flag;
    }

    @Override
    public TownRoomsHandle getRoomsHandle() {
        return flag.roomsHandle;
    }

    @Override
    public void setUpQuestsForNewlyPlacedFlag() {
        flag.setUpQuestsForNewlyPlacedFlag();
    }

    @Override
    public void setInitializedQuests(boolean b) {
        flag.isInitializedQuests = b;
    }

    @Override
    public TownQuestsHandle getQuests() {
        return flag.questsHandle;
    }

    @Override
    public MCMorningRewards getMorningRewards() {
        return flag.morningRewards;
    }

    @Override
    public TownPois getPOIs() {
        return flag.pois;
    }

    @Override
    public TownKnowledgeStore getKnowledge() {
        return flag.knowledgeHandle;
    }

    @Override
    public TownVillagerHandle getVillagers() {
        return flag.villagerHandle;
    }

    @Override
    public TownHealingHandle getHealing() {
        return flag.healing;
    }

    @Override
    public MCQuestBatches getQuestBatches() {
        return flag.quests.questBatches;
    }

    @Override
    public TownWorkHandle getWorkHandle() {
        return flag.workHandle;
    }
}
