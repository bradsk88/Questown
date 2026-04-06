package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuestBatches;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public interface TutorialTownView {

    String PHASE_KICKOFF = "kickoff";
    String PHASE_HUNTER_GATHERER = "hunterGatherer";
    String PHASE_JOB_CHANGE_FOOD = "jobChangeAndFood";
    String PHASE_NEW_JOB_ROOM = "newJobRoom";
    String PHASE_4A = "phase4a";
    String PHASE_4B = "phase4b";
    String PHASE_5 = "phase5";
    String PHASE_6 = "phase6";
    String PHASE_7 = "phase7";

    int villagerCount();

    boolean tutorialBopGranted();

    void grantBop(int amount);

    void markTutorialBopGranted();

    void broadcastMessage(String message);

    void broadcastTutorialToast(String titleKey, String descriptionKey);

    void fireTutorialTrigger(TutorialTrigger.Triggers trigger);

    ResourceLocation getExoticWood();

    @Nullable
    JobID chooseJobForTutorial(MCQuestBatches questBatches);

    ResourceLocation getRoomForJob(JobID job);

    MCReward makeReward(String phaseName);
}
