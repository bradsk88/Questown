package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.quests.MCDelayedReward;
import ca.bradj.questown.town.quests.MCInstantReward;
import ca.bradj.questown.town.quests.MCRewardList;
import ca.bradj.questown.town.rewards.*;

public class RewardsInit {

    private static final RewardsInit INSTANCE = new RewardsInit();

    private RewardsInit() {
    }

    public static final HolderRegistry<RewardType<?>> REWARD_TYPES = Loader.registry(ca.bradj.questown.town.rewards.Registry.Keys.REWARD_TYPES);

    public static final Holder<RewardType<MCRewardList>> LIST = REWARD_TYPES.register(
            MCRewardList.ID,
            () -> RewardType.Builder
                    .of(MCRewardList::new)
                    .build(Questown.ResourceLocation(MCRewardList.ID))
    );

    public static final Holder<RewardType<MCDelayedReward>> DELAYED = REWARD_TYPES.register(
            MCDelayedReward.ID,
            () -> RewardType.Builder
                    .of(MCDelayedReward::new)
                    .build(Questown.ResourceLocation(MCDelayedReward.ID))
    );


    public static final Holder<RewardType<MCInstantReward>> INSTANT = REWARD_TYPES.register(
            MCInstantReward.ID,
            () -> RewardType.Builder
                    .of(MCInstantReward::new)
                    .build(Questown.ResourceLocation(MCInstantReward.ID))
    );

    public static final Holder<RewardType<SpawnVisitorReward>> VISITOR = REWARD_TYPES.register(
            SpawnVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new SpawnVisitorReward(rType, flag, null))
                    .build(Questown.ResourceLocation(SpawnVisitorReward.ID))
    );

    public static final Holder<RewardType<AddBatchOfQuestsForVisitorReward>> RANDOM_BATCH_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddBatchOfQuestsForVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddBatchOfQuestsForVisitorReward(rType, flag, null))
                    .build(Questown.ResourceLocation(AddBatchOfQuestsForVisitorReward.ID))
    );

    public static final Holder<RewardType<CampfireReadyReward>> CAMPFIRE_READY
            = REWARD_TYPES.register(
            CampfireReadyReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new CampfireReadyReward(rType, flag))
                    .build(Questown.ResourceLocation(CampfireReadyReward.ID))
    );

    public static final Holder<RewardType<AddBasicsBatchReward>> BASICS_BATCH
            = REWARD_TYPES.register(
            AddBasicsBatchReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddBasicsBatchReward(rType, flag))
                    .build(Questown.ResourceLocation(AddBasicsBatchReward.ID))
    );

    public static final Holder<RewardType<AddRandomUpgradeQuest>> RANDOM_UPGRADE_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddRandomUpgradeQuest.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddRandomUpgradeQuest(rType, flag, INSTANCE))
                    .build(Questown.ResourceLocation(AddRandomUpgradeQuest.ID))
    );

    public static final Holder<RewardType<AddRandomJobQuestReward>> RANDOM_JOB_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddRandomJobQuestReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddRandomJobQuestReward(rType, flag, INSTANCE))
                    .build(Questown.ResourceLocation(AddRandomJobQuestReward.ID))
    );

    public static final Holder<RewardType<ChangeJobReward>> CHANGE_JOB
            = REWARD_TYPES.register(
            ChangeJobReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new ChangeJobReward(rType, flag, null, null))
                    .build(Questown.ResourceLocation(ChangeJobReward.ID))
    );

    public static final Holder<RewardType<AddItemQuestReward>> ITEM_QUEST
            = REWARD_TYPES.register(
            AddItemQuestReward.ID,
            () -> RewardType.Builder
                    .of(AddItemQuestReward::emptyForDeserializing)
                    .build(Questown.ResourceLocation(AddItemQuestReward.ID))
    );

    public static void boot() {}
}
