package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RegistryEvents {

    private static final int MAX_VARINT = Integer.MAX_VALUE - 1; // Copying Forge

    @SubscribeEvent()
    public static void register(NewRegistryEvent event) {
        event.create(
                makeRegistry(Registry.Keys.REWARD_TYPES, (Class<RewardType<?>>) (Class<?>) RewardType.class),
                reg -> Registry.REWARD_TYPES = reg
        );
    }

    private static <T extends IForgeRegistryEntry<T>> RegistryBuilder<T> makeRegistry(ResourceKey<? extends net.minecraft.core.Registry<T>> key, Class<T> type)
    {
        return new RegistryBuilder<T>().setName(key.location()).setType(type).setMaxID(MAX_VARINT);
    }
}
