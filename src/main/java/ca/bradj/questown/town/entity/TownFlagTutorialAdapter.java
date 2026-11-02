package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.world.item.ItemStack;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.GrantBlockOfProgressReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static ca.bradj.questown.town.entity.TownQuests.defaultQuestCompletionRewards;

class TownFlagTutorialAdapter implements TutorialTownView {

    private final TownFlagBlockEntity flag;
    private final MCQuestBatches questBatches;

    private TownFlagTutorialAdapter(TownFlagBlockEntity flag, MCQuestBatches questBatches) {
        this.flag = flag;
        this.questBatches = questBatches;
    }

    static TutorialTownView from(TownFlagBlockEntity flag, MCQuestBatches questBatches) {
        return new TownFlagTutorialAdapter(flag, questBatches);
    }

    @Override
    public int villagerCount() {
        return flag.getVillagerHandle().entities().size();
    }

    @Override
    public boolean anyVillagerOnRoot(String rootId) {
        return flag.getVillagerHandle().getJobs().stream().anyMatch(j -> rootId.equals(j.rootId()));
    }

    @Override
    public boolean tutorialBopGranted() {
        return flag.tutorialBopGranted;
    }

    @Override
    public void grantBop(int amount) {
        for (int i = 0; i < amount; i++) {
            flag.bopStacks.add(new ItemStack(ItemsInit.BLOCK_OF_PROGRESS.get()));
        }
        flag.setChanged();
        flag.syncBopFull();
    }

    @Override
    public void markTutorialBopGranted() {
        flag.tutorialBopGranted = true;
        flag.setChanged();
    }

    @Override
    public void broadcastMessage(String message) {
        flag.messages.broadcastMessage(message);
    }

    @Override
    public void broadcastTutorialToast(String titleKey, String descriptionKey) {
        flag.messages.broadcastTutorialToast(titleKey, descriptionKey);
    }

    @Override
    public void fireTutorialTrigger(TutorialTrigger.Triggers trigger) {
        if (!(flag.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        AdvancementsInit.TUTORIAL_TRIGGER.triggerForNearestPlayer(sl, trigger, flag.getBlockPos());
    }


    @Override
    public ResourceLocation getRoomForJob(JobID job) {
        return ServerJobsRegistry.getRoomForJobId(job);
    }

    @Override
    public MCReward makeReward(String phaseName) {
        return switch (phaseName) {
            case TutorialTownView.PHASE_KICKOFF -> new MCInstantReward(flag, new AddBatchOfQuestsForVisitorReward(flag, null));
            // Villagers 2 and 3 always arrive, so the miner lesson is always reached.
            case TutorialTownView.PHASE_HUNTER_GATHERER, TutorialTownView.PHASE_JOB_CHANGE_FOOD -> arrivalWithQuestBatch();
            // The cook-job batch pays the Block of Progress the cook skill costs.
            case TutorialTownView.PHASE_COOK_JOB -> new MCInstantReward(flag, new GrantBlockOfProgressReward(flag, 1));
            // Path batches: some bring a villager, with no quest batch of its own.
            case TutorialTownView.PHASE_PATH_ARRIVAL -> arrivalOnly();
            case TutorialTownView.PHASE_PATH -> new MCRewardList(flag);
            // The miner batch brings villager 4, the one a path's first job change uses.
            case TutorialTownView.PHASE_MINER -> arrivalOnly();
            case TutorialTownView.PHASE_NEW_JOB_ROOM -> new MCDelayedReward(flag, defaultQuestCompletionRewards(flag));
            default -> new AddBatchOfQuestsForVisitorReward(flag, null);
        };
    }

    @Override
    public void provisionArrival(
            MCReward reward,
            Collection<ResourceLocation> wanted,
            Consumer<ResourceLocation> addRoomQuest
    ) {
        TownQuests.provisionArrival(flag, reward, wanted, addRoomQuest);
    }

    @Override
    public void revealSkill(JobID skill) {
        flag.getTownVillagerHandle().revealSkill(skill);
    }

    private MCReward arrivalOnly() {
        return new MCDelayedReward(flag, new SpawnVisitorReward(flag, VillagerUUID.random()));
    }

    /** A morning arrival with its quest batch, which belongs to the villager who arrives. */
    private MCReward arrivalWithQuestBatch() {
        VillagerUUID nextVisitorUUID = VillagerUUID.random();
        return new MCRewardList(
                flag,
                new MCDelayedReward(flag, new SpawnVisitorReward(flag, nextVisitorUUID)),
                new AddBatchOfQuestsForVisitorReward(flag, UUID.fromString(VillagerUUID.getStringUUID(nextVisitorUUID)))
        );
    }


}
