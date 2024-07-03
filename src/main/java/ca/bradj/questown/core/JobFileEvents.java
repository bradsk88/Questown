package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.ResourceJobLoader;
import ca.bradj.questown.jobs.Works;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JobFileEvents {

    @SubscribeEvent
    public static void reloadListeners(ServerStartingEvent event) {
        ResourceJobLoader.LISTENER.loadFromFiles(event.getServer().getResourceManager());
        Works.staticInitialize(ResourceJobLoader.LISTENER.getJobs());
    }

}
