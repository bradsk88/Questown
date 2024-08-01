package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Events {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        Compat.enqueueOrLog(event, DeclarativeJobs::staticInitialize);
    }
}
