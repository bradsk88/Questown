package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SyncWorkForCommandsMessage;
import ca.bradj.questown.jobs.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), ms);
    }

}
