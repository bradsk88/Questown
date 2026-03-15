package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuestBatches;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

class TestTutorialTownView implements TutorialTownView {

    private int villagerCount;
    private boolean tutorialBopGranted;
    private int bopGranted;
    private final List<String> messagesSent = new ArrayList<>();
    private final List<TutorialTrigger.Triggers> triggersFired = new ArrayList<>();
    private ResourceLocation exoticWood;
    private JobID jobForTutorial;
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
    public void fireTutorialTrigger(TutorialTrigger.Triggers trigger) {
        triggersFired.add(trigger);
    }

    @Override
    public ResourceLocation getExoticWood() {
        return exoticWood;
    }

    @Override
    @Nullable
    public JobID chooseJobForTutorial(MCQuestBatches questBatches) {
        return jobForTutorial;
    }

    @Override
    public ResourceLocation getRoomForJob(JobID job) {
        return roomForJob;
    }

    @Override
    public MCReward makeReward(String phaseName) {
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

    static class Builder {
        private int villagerCount = 1;
        private boolean tutorialBopGranted = false;
        private ResourceLocation exoticWood = new ResourceLocation("minecraft", "dark_oak_log");
        private JobID jobForTutorial = new JobID("farmer", "wheat");
        private ResourceLocation roomForJob = new ResourceLocation("questown", "farm");

        Builder villagerCount(int count) {
            this.villagerCount = count;
            return this;
        }

        Builder tutorialBopGranted(boolean granted) {
            this.tutorialBopGranted = granted;
            return this;
        }

        Builder exoticWood(ResourceLocation wood) {
            this.exoticWood = wood;
            return this;
        }

        Builder jobForTutorial(JobID job) {
            this.jobForTutorial = job;
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
            view.exoticWood = exoticWood;
            view.jobForTutorial = jobForTutorial;
            view.roomForJob = roomForJob;
            return view;
        }
    }
}
