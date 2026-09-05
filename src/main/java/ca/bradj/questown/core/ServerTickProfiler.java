package ca.bradj.questown.core;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.TickProfile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Dev-only whole-server-tick measurement. The {@link TickProfile} flag-tick buckets are
 * only one phase of the server tick, so a whole-server stall (the log9 "Can't keep up!
 * Running 2008ms" — a 2s server tick while the flag tick stayed at 0.6ms) is invisible in
 * them. This wraps the server tick on its {@code START}/{@code END} boundary and records the
 * whole-tick duration into {@link TickProfile#recordServer}, so the stall shows as a number
 * the {@code perf/*} autotest scenarios can read.
 *
 * <p>Inert in shipped jars via {@link FMLEnvironment#production}, and off the production path
 * when {@link TickProfile} is disabled (the listener still fires, but {@code recordServer}
 * pays one boolean check and returns). Same guard the agent bridge uses, so it never affects
 * real players or a production world.
 */
public final class ServerTickProfiler {

    private long start;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            start = System.nanoTime();
        } else if (event.phase == TickEvent.Phase.END) {
            long elapsed = System.nanoTime() - start;
            TickProfile.INSTANCE.recordServer(elapsed);
            // Dev-only: print a whole-server-tick stall so a "Can't keep up" cause is
            // visible even when the flag-tick buckets are fast. The flag tick is only
            // one phase of the server tick, so a 900ms server tick can happen while the
            // flag tick is ~260ms (biomes) — the extra time is what this surfaces.
            if (elapsed > 100_000_000L) {
                QT.FLAG_LOGGER.warn(
                    "[tickprofile] server-tick stall = {}ms", elapsed / 1_000_000
                );
            }
        }
    }
}
