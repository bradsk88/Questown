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
            // The basics batch brings no villager: villager 2 is the hunter-gatherer batch's
            // reward (dev ruling 2026-10-18; both used to queue one and they landed the same
            // morning once the second waited on raw food).
            case TutorialTownView.PHASE_KICKOFF -> new MCInstantReward(flag, new AddBatchOfQuestsForVisitorReward(flag, null));
            // Villager 2 (hunter-gatherer) and villager 3 (cook) are fixed onboarding arrivals;
            // the cook's used to be defaultQuestCompletionRewards, a coin flip once villager 2
            // had quests, which could leave the miner beat (3 villagers) waiting forever.
            case TutorialTownView.PHASE_HUNTER_GATHERER, TutorialTownView.PHASE_JOB_CHANGE_FOOD -> arrivalWithQuestBatch();
            // The cook-job batch pays the Block of Progress its follow-up's cook skill costs.
            case TutorialTownView.PHASE_COOK_JOB -> new MCInstantReward(flag, new GrantBlockOfProgressReward(flag, 1));
            case TutorialTownView.PHASE_NEW_JOB_ROOM, TutorialTownView.PHASE_MINER ->
                    new MCDelayedReward(flag, defaultQuestCompletionRewards(flag));
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

    /** A morning arrival plus its quest batch, under one id so the batch belongs to the villager who arrives. */
    private MCReward arrivalWithQuestBatch() {
        VillagerUUID nextVisitorUUID = VillagerUUID.random();
        return new MCRewardList(
                flag,
                new MCDelayedReward(flag, new SpawnVisitorReward(flag, nextVisitorUUID)),
                new AddBatchOfQuestsForVisitorReward(flag, UUID.fromString(VillagerUUID.getStringUUID(nextVisitorUUID)))
        );
    }


}
