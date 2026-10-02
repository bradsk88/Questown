package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.rewards.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@net.neoforged.fml.common.EventBusSubscriber(modid = Questown.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class RegistryEvents {

    private static final int MAX_VARINT = Integer.MAX_VALUE - 1; // Copying Forge

    @SubscribeEvent()
    public static void register(NewRegistryEvent event) {
        Registry.REWARD_TYPES = event.create(makeRegistry(Registry.Keys.REWARD_TYPES));
    }

    private static <T> RegistryBuilder<T> makeRegistry(ResourceKey<? extends net.minecraft.core.Registry<T>> key)
    {
        return new RegistryBuilder<T>(key).maxId(MAX_VARINT);
    }
}
