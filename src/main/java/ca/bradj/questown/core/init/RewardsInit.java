package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.quests.MCRewardList;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.Registry;
import ca.bradj.questown.town.rewards.RewardType;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class RewardsInit {

    public static final DeferredRegister<RewardType<?>> REWARD_TYPES = DeferredRegister.create(
            Registry.Keys.REWARD_TYPES,
            Questown.MODID
    );

    public static final RegistryObject<RewardType<MCRewardList>> LIST = REWARD_TYPES.register(
            MCRewardList.ID,
            () -> RewardType.Builder
                    .of(MCRewardList::new)
                    .build(new ResourceLocation(Questown.MODID, MCRewardList.ID).toString())
    );

    public static final RegistryObject<RewardType<SpawnVisitorReward>> VISITOR = REWARD_TYPES.register(
            SpawnVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new SpawnVisitorReward(rType, flag, null))
                    .build(new ResourceLocation(Questown.MODID, SpawnVisitorReward.ID).toString())
    );

    public static final RegistryObject<RewardType<AddBatchOfRandomQuestsForVisitorReward>> RANDOM_BATCH_FOR_VILLAGER
            = REWARD_TYPES.register(
            AddBatchOfRandomQuestsForVisitorReward.ID,
            () -> RewardType.Builder
                    .of((rType, flag) -> new AddBatchOfRandomQuestsForVisitorReward(rType, flag, null))
                    .build(new ResourceLocation(Questown.MODID, AddBatchOfRandomQuestsForVisitorReward.ID).toString())
    );

    public static void register(IEventBus bus) {
        REWARD_TYPES.register(bus);
    }
}
