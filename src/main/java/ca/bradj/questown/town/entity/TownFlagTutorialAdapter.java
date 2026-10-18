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
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;

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
    public ResourceLocation getExoticWood() {
        return getExoticWoodForTown(flag);
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
            case TutorialTownView.PHASE_NEW_JOB_ROOM, TutorialTownView.PHASE_7, TutorialTownView.PHASE_MINER ->
                    new MCDelayedReward(flag, defaultQuestCompletionRewards(flag));
            default -> new AddBatchOfQuestsForVisitorReward(flag, null);
        };
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

    static ResourceLocation chooseExoticWood(String biomePath) {
        Map<String, ResourceLocation> nativeWoods = Map.of(
                "forest", Compat.getItemId(Items.OAK_LOG),
                "birch", Compat.getItemId(Items.BIRCH_LOG),
                "taiga", Compat.getItemId(Items.SPRUCE_LOG),
                "jungle", Compat.getItemId(Items.JUNGLE_LOG),
                "savanna", Compat.getItemId(Items.ACACIA_LOG),
                "dark_forest", Compat.getItemId(Items.DARK_OAK_LOG),
                "mangrove", Compat.getItemId(Items.MANGROVE_LOG)
        );

        ImmutableList<ResourceLocation> allWoods = ImmutableList.of(
                Compat.getItemId(Items.OAK_LOG),
                Compat.getItemId(Items.BIRCH_LOG),
                Compat.getItemId(Items.SPRUCE_LOG),
                Compat.getItemId(Items.JUNGLE_LOG),
                Compat.getItemId(Items.ACACIA_LOG),
                Compat.getItemId(Items.DARK_OAK_LOG)
        );

        ResourceLocation nativeWood = null;
        for (Map.Entry<String, ResourceLocation> entry : nativeWoods.entrySet()) {
            if (biomePath.contains(entry.getKey())) {
                nativeWood = entry.getValue();
                break;
            }
        }

        for (ResourceLocation wood : allWoods) {
            if (!wood.equals(nativeWood)) {
                return wood;
            }
        }

        return Compat.getItemId(Items.DARK_OAK_LOG);
    }

    private static ResourceLocation getExoticWoodForTown(TownFlagBlockEntity t) {
        if (!(t.getLevel() instanceof ServerLevel sl)) {
            return Compat.getItemId(Items.DARK_OAK_LOG);
        }
        String biome = sl.getBiome(t.getBlockPos()).unwrapKey()
                          .map(k -> k.location().getPath())
                          .orElse("plains");
        return chooseExoticWood(biome);
    }
}
