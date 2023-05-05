package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

public class Registry {

    public static class Keys {
        public static final ResourceKey<net.minecraft.core.Registry<RewardType<?>>> REWARD_TYPES
                = key("reward_types");
    }

    public static IForgeRegistry<RewardType<?>> REWARD_TYPES;

    private static <T> ResourceKey<net.minecraft.core.Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(new ResourceLocation(Questown.MODID, name));
    }

}
