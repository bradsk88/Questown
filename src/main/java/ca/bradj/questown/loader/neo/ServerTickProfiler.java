package ca.bradj.questown.loader.neo;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.TickProfile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dev-only: measures the whole server tick for the perf scenarios. Off in released jars. */
public final class ServerTickProfiler {

    private long start;

    @SubscribeEvent
    public void onTickPre(ServerTickEvent.Pre event) {
        if (FMLEnvironment.production) {
            return;
        }
        start = System.nanoTime();
    }

    @SubscribeEvent
    public void onTickPost(ServerTickEvent.Post event) {
        if (FMLEnvironment.production) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        TickProfile.INSTANCE.recordServer(elapsed);
        // Dev-only: log a slow server tick.
        if (elapsed > 100_000_000L) {
            QT.FLAG_LOGGER.warn(
                "[tickprofile] server-tick stall = {}ms", elapsed / 1_000_000
            );
        }
    }
}
