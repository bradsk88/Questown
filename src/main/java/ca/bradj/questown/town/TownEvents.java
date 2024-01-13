package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TownEvents {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        event.enqueueWork(TownVillagerMoods::staticInitialize);
    }
}
