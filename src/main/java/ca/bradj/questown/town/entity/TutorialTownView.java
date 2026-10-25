package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.function.Consumer;


public interface TutorialTownView {

    String PHASE_KICKOFF = "kickoff";
    String PHASE_HUNTER_GATHERER = "hunterGatherer";
    String PHASE_EMPTY_ROOMS = "emptyRooms";
    String PHASE_JOB_CHANGE_FOOD = "jobChangeAndFood";
    String PHASE_COOK_JOB = "cookJob";
    String PHASE_MINER = "miner";
    String PHASE_NEW_JOB_ROOM = "newJobRoom";
    String PHASE_4A = "phase4a";
    String PHASE_4B = "phase4b";
    String PHASE_5 = "phase5";

    int villagerCount();

    boolean tutorialBopGranted();

    void grantBop(int amount);

    void markTutorialBopGranted();

    void broadcastMessage(String message);

    void broadcastTutorialToast(String titleKey, String descriptionKey);

    void fireTutorialTrigger(TutorialTrigger.Triggers trigger);


    ResourceLocation getRoomForJob(JobID job);

    MCReward makeReward(String phaseName);

    /**
     * Adds the bed and storage quests a villager-bringing {@code reward} needs
     * ({@code TownQuests.provisionArrival}). {@code wanted} is the batch's room ids so far.
     */
    void provisionArrival(MCReward reward, Collection<ResourceLocation> wanted, Consumer<ResourceLocation> addRoomQuest);
}
