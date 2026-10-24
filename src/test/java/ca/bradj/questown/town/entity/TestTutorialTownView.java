package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

class TestTutorialTownView implements TutorialTownView {

    // Arrival provisioning reads the live town's beds and rewards; the planner is pinned by
    // ArrivalProvisionsTest, so tutorial-flow tests leave batches as built.
    @Override
    public void provisionArrival(
            ca.bradj.questown.town.quests.MCReward reward,
            java.util.Collection<net.minecraft.resources.ResourceLocation> wanted,
            java.util.function.Consumer<net.minecraft.resources.ResourceLocation> addRoomQuest
    ) {
    }

    private int villagerCount;
    private boolean tutorialBopGranted;
    private int bopGranted;
    private final List<String> messagesSent = new ArrayList<>();
    private final List<TutorialTrigger.Triggers> triggersFired = new ArrayList<>();
    private final List<String> rewardPhaseNames = new ArrayList<>();
    private ResourceLocation roomForJob;

    private TestTutorialTownView() {
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public int villagerCount() {
        return villagerCount;
    }

    @Override
    public boolean tutorialBopGranted() {
        return tutorialBopGranted;
    }

    @Override
    public void grantBop(int amount) {
        bopGranted += amount;
    }

    @Override
    public void markTutorialBopGranted() {
        tutorialBopGranted = true;
    }

    @Override
    public void broadcastMessage(String message) {
        messagesSent.add(message);
    }

    @Override
    public void broadcastTutorialToast(String titleKey, String descriptionKey) {
        messagesSent.add(titleKey + ":" + descriptionKey);
    }

    @Override
    public void fireTutorialTrigger(TutorialTrigger.Triggers trigger) {
        triggersFired.add(trigger);
    }


    @Override
    public ResourceLocation getRoomForJob(JobID job) {
        return roomForJob;
    }

    @Override
    public MCReward makeReward(String phaseName) {
        rewardPhaseNames.add(phaseName);
        return new NoOpReward();
    }

    List<String> getMessagesSent() {
        return messagesSent;
    }

    List<TutorialTrigger.Triggers> getTriggersFired() {
        return triggersFired;
    }

    int getBopGranted() {
        return bopGranted;
    }

    List<String> getRewardPhaseNames() {
        return rewardPhaseNames;
    }

    static class Builder {
        private int villagerCount = 1;
        private boolean tutorialBopGranted = false;
        private ResourceLocation roomForJob = new ResourceLocation("questown", "farm");

        Builder villagerCount(int count) {
            this.villagerCount = count;
            return this;
        }

        Builder tutorialBopGranted(boolean granted) {
            this.tutorialBopGranted = granted;
            return this;
        }


        Builder roomForJob(ResourceLocation room) {
            this.roomForJob = room;
            return this;
        }

        TestTutorialTownView build() {
            TestTutorialTownView view = new TestTutorialTownView();
            view.villagerCount = villagerCount;
            view.tutorialBopGranted = tutorialBopGranted;
            view.roomForJob = roomForJob;
            return view;
        }
    }
}
