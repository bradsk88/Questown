package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.rewards.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegistryBuilder;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = Questown.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public class RegistryEvents {

    private static final int MAX_VARINT = Integer.MAX_VALUE - 1;

    @SubscribeEvent()
    public static void register(NewRegistryEvent event) {
        event.create(makeRegistry(Registry.Keys.REWARD_TYPES), reg -> Registry.REWARD_TYPES = new Registry.RewardTypes(reg));
    }

    private static <T> RegistryBuilder<T> makeRegistry(ResourceKey<? extends net.minecraft.core.Registry<T>> key)
    {
        return new RegistryBuilder<T>().setName(key.location()).setMaxID(MAX_VARINT);
    }
}
