package ca.bradj.questown.loader.neo;

import ca.bradj.questown.commands.test.TestAllExecutor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Game-bus tick binder for the {@code /_qtdev testall} runner: pumps
 * {@link TestAllExecutor#tick()} once per server tick and unregisters itself the first tick it
 * reports done. Lives here (not next to the command) so the command leaf never names the
 * event-bus or the tick-event type.
 */
public class TestAllTickListener {

    private final TestAllExecutor executor;

    public TestAllTickListener(TestAllExecutor executor) {
        this.executor = executor;
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (executor.tick()) {
            NeoForge.EVENT_BUS.unregister(this);
        }
    }
}
