package ca.bradj.questown.integration;

import ca.bradj.questown.Questown;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class IntegrationForgeEvents {

    @SubscribeEvent()
    public static void register(ServerAboutToStartEvent event) {
        SpecialRulesRegistry.finalizeForServer();
    }
}
