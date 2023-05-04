package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
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

    public static final RegistryObject<RewardType<SpawnVisitorReward>> VISITOR;

    static {
        VISITOR = REWARD_TYPES.register(
                SpawnVisitorReward.ID,
                () -> RewardType.Builder
                        .of(SpawnVisitorReward::new)
                        .build(new ResourceLocation(Questown.MODID, SpawnVisitorReward.ID).toString())
        );
    }

    public static void register(IEventBus bus) {
        REWARD_TYPES.register(bus);
    }
}
