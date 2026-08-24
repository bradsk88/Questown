package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.CoreProgression;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

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
        flag.bopCount += amount;
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
    @Nullable
    public JobID chooseJobForTutorial(MCQuestBatches questBatches) {
        WorksBehaviour.TownData data = flag.getTownData();
        CoreProgression<JobID, ResourceLocation> prog = new CoreProgression<>(
                flag.getEconomicsHandle().getAggregatedItems(null),
                GathererUnmappedNoToolWorkQtrDay.ID::equals,
                jobs -> Compat.shuffle(jobs.iterator(), flag.getServerLevel()).get(0),
                j -> producesFood(j),
                (j, needs) -> resourceImpact(needs, data, j)
        );
        return prog.getFirstJobChange(
                ServerJobsRegistry.getAllRootJobs(),
                ServerJobsRegistry::getRoomForJobId,
                q -> questBatches.includes(v -> q.equals(v.getWantedId()))
        );
    }

    @Override
    public ResourceLocation getRoomForJob(JobID job) {
        return ServerJobsRegistry.getRoomForJobId(job);
    }

    @Override
    public MCReward makeReward(String phaseName) {
        return switch (phaseName) {
            case TutorialTownView.PHASE_KICKOFF -> new MCRewardList(
                    flag,
                    new MCDelayedReward(flag, new SpawnVisitorReward(flag, VillagerUUID.random())),
                    new MCInstantReward(flag, new AddBatchOfQuestsForVisitorReward(flag, null))
            );
            case TutorialTownView.PHASE_HUNTER_GATHERER -> {
                VillagerUUID nextVisitorUUID = VillagerUUID.random();
                yield new MCRewardList(
                        flag,
                        new MCDelayedReward(flag, new SpawnVisitorReward(flag, VillagerUUID.random())),
                        new AddBatchOfQuestsForVisitorReward(flag, VillagerUUID.get(nextVisitorUUID))
                );
            }
            case TutorialTownView.PHASE_NEW_JOB_ROOM, TutorialTownView.PHASE_7 ->
                    new MCDelayedReward(flag, defaultQuestCompletionRewards(flag));
            default -> new AddBatchOfQuestsForVisitorReward(flag, null);
        };
    }

    private boolean producesFood(JobID j) {
        return ServerJobsRegistry.getResults(flag.getTownData(), j).stream().anyMatch(
                i -> Ingredient.of(ca.bradj.questown.core.init.TagsInit.Items.VILLAGER_FOOD).test(i.toMCItemStack())
        );
    }

    private Collection<ItemEconomicsData> resourceImpact(
            ImmutableList<ItemEconomicsData> needs,
            WorksBehaviour.TownData data,
            JobID j
    ) {
        ImmutableSet<MCTownItem> results = ServerJobsRegistry.getResults(data, j);
        return needs.stream().filter(v -> results.stream().anyMatch(z -> sameItem(v, z))).toList();
    }

    private static boolean sameItem(ItemEconomicsData v, MCTownItem z) {
        return ca.bradj.questown.gui.Ingredients.fromString(v.ingredientKey()).test(z.toMCItemStack());
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
