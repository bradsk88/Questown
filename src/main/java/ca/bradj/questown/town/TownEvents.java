package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = Questown.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class TownEvents {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        Compat.enqueueOrLog(TownFlagBlockEntity::staticInitialize);
        Compat.enqueueOrLog(TownVillagerMoods::staticInitialize);
    }
}
