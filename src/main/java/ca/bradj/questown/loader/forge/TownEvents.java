package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.TownVillagerMoods;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = Questown.MODID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class TownEvents {

    @SubscribeEvent()
    public static void register(FMLCommonSetupEvent event) {
        Compat.enqueueOrLog(TownFlagBlockEntity::staticInitialize);
        Compat.enqueueOrLog(TownVillagerMoods::staticInitialize);
    }
}
