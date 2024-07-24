package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntegrationForgeEvents {

    @SubscribeEvent()
    public static void register(ServerAboutToStartEvent event) {
        SpecialRulesRegistry.finalizeForServer();
    }
}
