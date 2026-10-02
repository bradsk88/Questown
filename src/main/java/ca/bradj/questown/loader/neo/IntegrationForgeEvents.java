package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class IntegrationForgeEvents {

    @SubscribeEvent()
    public static void register(ServerAboutToStartEvent event) {
        SpecialRulesRegistry.finalizeForServer();
    }
}
