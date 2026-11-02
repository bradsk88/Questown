package ca.bradj.questown.loader.neo;

import ca.bradj.questown.commands.test.TestExecutor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Runs {@code /_qtdev test} one step per tick until it is done. */
public class TestTickListener {

    private final TestExecutor executor;

    public TestTickListener(TestExecutor executor) {
        this.executor = executor;
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (executor.tick()) {
            NeoForge.EVENT_BUS.unregister(this);
        }
    }
}
