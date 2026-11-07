package ca.bradj.questown.loader.forge;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.TickProfile;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Dev-only: measures the whole server tick for the perf scenarios. Off in released jars. */
public final class ServerTickProfiler {

    private long start;

    @SubscribeEvent
    public void onTickPre_qstp25(net.minecraftforge.event.TickEvent.ServerTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) { return; }
        if (FMLEnvironment.production) {
            return;
        }
        start = System.nanoTime();
    }

    @SubscribeEvent
    public void onTickPost_qstp33(net.minecraftforge.event.TickEvent.ServerTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) { return; }
        if (FMLEnvironment.production) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        TickProfile.INSTANCE.recordServer(elapsed);
        if (elapsed > 100_000_000L) {
            QT.FLAG_LOGGER.warn(
                "[tickprofile] server-tick stall = {}ms", elapsed / 1_000_000
            );
        }
    }
}
