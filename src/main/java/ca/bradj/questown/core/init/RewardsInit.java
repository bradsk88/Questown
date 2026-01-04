package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.quests.MCDelayedReward;
import ca.bradj.questown.town.quests.MCInstantReward;
import ca.bradj.questown.town.quests.MCRewardList;
import ca.bradj.questown.town.rewards.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class RewardsInit {

    private static final RewardsInit INSTANCE = new RewardsInit();

    private RewardsInit() {
    }

    public static final DeferredRegister<RewardType<?>> REWARD_TYPES = DeferredRegister.create(
            Registry.Keys.REWARD_TYPES,
            Questown.MODID
    );

    public static final RegistryObject<RewardType<MCRewardList>> LIST = REWARD_TYPES.register(
            MCRewardList.ID,
            () -> RewardType.Builder
                    .of(MCRewardList::new)
                    .build(Questown.ResourceLocation(MCRewardList.ID))
    );

    public static final RegistryObject<RewardType<MCDelayedReward>> DELAYED = REWARD_TYPES.register(
            MCDelayedReward.ID,
            () -> RewardType.Builder
                    .of(MCDelayedReward::new)
                    .build(Questown.ResourceLocation(MCDelayedReward.ID))
    );


    public static final RegistryObject<RewardType<MCInstantReward>> INSTANT = REWARD_TYPES.register(
            MCInstantReward.ID,
            () -> RewardType.Builder
                    .of(MCInstantReward::new)
                    .build(Questown.ResourceLocation(MCInstantReward.ID))
    );

    public static final RegistryObject<RewardType<SpawnVisitorReward>> VISITOR = REWARD_TYPES.register(
            SpawnVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new SpawnVisitorReward(rType, flag, null))
                    .build(Questown.ResourceLocation(SpawnVisitorReward.ID))
    );

    public static final RegistryObject<RewardType<AddBatchOfQuestsForVisitorReward>> RANDOM_BATCH_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddBatchOfQuestsForVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddBatchOfQuestsForVisitorReward(rType, flag, null))
                    .build(Questown.ResourceLocation(AddBatchOfQuestsForVisitorReward.ID))
    );

    public static final RegistryObject<RewardType<AddRandomUpgradeQuest>> RANDOM_UPGRADE_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddRandomUpgradeQuest.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddRandomUpgradeQuest(rType, flag, INSTANCE))
                    .build(Questown.ResourceLocation(AddRandomUpgradeQuest.ID))
    );

    public static final RegistryObject<RewardType<AddRandomJobQuestReward>> RANDOM_JOB_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddRandomJobQuestReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddRandomJobQuestReward(rType, flag, INSTANCE))
                    .build(Questown.ResourceLocation(AddRandomJobQuestReward.ID))
    );

    public static final RegistryObject<RewardType<ChangeJobReward>> CHANGE_JOB
            = REWARD_TYPES.register(
            ChangeJobReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new ChangeJobReward(rType, flag, null, null))
                    .build(Questown.ResourceLocation(ChangeJobReward.ID))
    );

    public static final RegistryObject<RewardType<AddItemQuestReward>> ITEM_QUEST
            = REWARD_TYPES.register(
            AddItemQuestReward.ID,
            () -> RewardType.Builder
                    .of(AddItemQuestReward::emptyForDeserializing)
                    .build(Questown.ResourceLocation(AddItemQuestReward.ID))
    );

    public static void register(IEventBus bus) {
        REWARD_TYPES.register(bus);
    }
}
