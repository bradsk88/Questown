package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.Works;
import ca.bradj.questown.mc.Compat;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TownEvents {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        Compat.enqueueOrLog(event, TownFlagBlockEntity::staticInitialize);
        Compat.enqueueOrLog(event, TownVillagerMoods::staticInitialize);
        Compat.enqueueOrLog(event, Works::staticInitialize);
    }
}
