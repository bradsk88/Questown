package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;


public class Registry {

    public static class Keys {
        public static final ResourceKey<net.minecraft.core.Registry<RewardType<?>>> REWARD_TYPES
                = key("reward_types");
    }

    /** Filled at NewRegistryEvent (1.19.2: a Forge registry; the 1.20.6 branch holds a vanilla one). */
    public static RewardTypes REWARD_TYPES;

    /** The one lookup callers use, over Forge's registry. */
    public record RewardTypes(net.minecraftforge.registries.IForgeRegistry<RewardType<?>> forge) {
        public RewardType<?> get(ResourceLocation id) {
            return forge.getValue(id);
        }
    }

    private static <T> ResourceKey<net.minecraft.core.Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(new ResourceLocation(Questown.MODID, name));
    }

}
