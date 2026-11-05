package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;


public class Registry {

    public static class Keys {
        public static final ResourceKey<net.minecraft.core.Registry<RewardType<?>>> REWARD_TYPES
                = key("reward_types");
    }

    public static RewardTypes REWARD_TYPES;

    public record RewardTypes(net.minecraftforge.registries.IForgeRegistry<RewardType<?>> forge) {
        public RewardType<?> get(ResourceLocation id) {
            return forge.getValue(id);
        }
    }

    private static <T> ResourceKey<net.minecraft.core.Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(new ResourceLocation(Questown.MODID, name));
    }

}
