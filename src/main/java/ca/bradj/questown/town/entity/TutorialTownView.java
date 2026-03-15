package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuestBatches;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public interface TutorialTownView {
    int villagerCount();

    boolean tutorialBopGranted();

    void grantBop(int amount);

    void markTutorialBopGranted();

    void broadcastMessage(String message);

    void fireTutorialTrigger(TutorialTrigger.Triggers trigger);

    ResourceLocation getExoticWood();

    @Nullable
    JobID chooseJobForTutorial(MCQuestBatches questBatches);

    ResourceLocation getRoomForJob(JobID job);

    MCReward makeReward(String phaseName);
}
