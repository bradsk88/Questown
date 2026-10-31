package ca.bradj.questown.loader.forge;

import ca.bradj.questown.commands.test.TestExecutor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Game-bus tick binder for the {@code /_qtdev test} runner: pumps {@link TestExecutor#tick()}
 * once per server tick and unregisters itself the first tick it reports done. Lives here (not
 * next to the command) so the command leaf never names the event-bus or the tick-event type.
 */
public class TestTickListener {

    private final TestExecutor executor;

    public TestTickListener(TestExecutor executor) {
        this.executor = executor;
    }

    @SubscribeEvent
    public void onTick_qttl20(net.minecraftforge.event.TickEvent.ServerTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) { return; }
        if (executor.tick()) {
            MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
}
