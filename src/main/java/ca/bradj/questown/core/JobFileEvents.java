package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncWorkForCommandsMessage;
import ca.bradj.questown.jobs.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

;

@net.neoforged.fml.common.EventBusSubscriber(modid = Questown.MODID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class JobFileEvents {

    @SubscribeEvent
    public static void reloadListeners(ServerAboutToStartEvent event) {
        ResourceJobLoader.LISTENER.loadFromFiles(event.getServer().getResourceManager());
        ImmutableMap<JobID, Work> jobs = ResourceJobLoader.LISTENER.getJobs();
        Works.staticInitialize(jobs);
        ServerJobsRegistry.staticInitialize(jobs);
    }

    @SubscribeEvent
    public static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ImmutableList<JobID> ids = ImmutableList.copyOf(Works.ids());
        SyncWorkForCommandsMessage ms = new SyncWorkForCommandsMessage(ids);
        QuestownNetwork.sendToPlayer(sp, ms);
    }

}
