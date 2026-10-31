package ca.bradj.questown.loader.forge;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class IntegrationForgeEvents {

    @SubscribeEvent()
    public static void register(ServerAboutToStartEvent event) {
        SpecialRulesRegistry.finalizeForServer();
    }
}
