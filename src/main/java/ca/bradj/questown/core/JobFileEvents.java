package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.*;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JobFileEvents {

    @SubscribeEvent
    public static void reloadListeners(ServerAboutToStartEvent event) {
        ResourceJobLoader.LISTENER.loadFromFiles(event.getServer().getResourceManager());
        ImmutableMap<JobID, Work> jobs = ResourceJobLoader.LISTENER.getJobs();
        Works.staticInitialize(jobs);
        JobsRegistry.staticInitialize(jobs);
    }

}
