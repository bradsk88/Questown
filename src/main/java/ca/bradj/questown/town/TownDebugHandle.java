package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.town.interfaces.DebugHandle;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class TownDebugHandle implements DebugHandle {

    private @Nullable Supplier<Boolean> debugTask;
    private boolean debugMode;

    private boolean cacheOff = false;
    private BiFunction<String, String, Void> broadcaster = (msg, data) -> {
        QT.INIT_LOGGER.error("(No broadcaster) {}: {}", msg, data);
        return null;
    };

    void initialize(BiFunction<String, String, Void> broadcaster) {
        this.broadcaster = broadcaster;
        this.cacheOff = !Config.CACHE_ENABLED_ON_START.get();
    }

    @Override
    public void toggleCache() {
        this.cacheOff = !this.cacheOff;
        broadcaster.apply("message.cache_mode", this.cacheOff ? "disabled" : "enabled");
    }

    @Override
    public boolean runDebugTask() {
        if (debugMode) {
            if (debugTask != null) {
                boolean done = debugTask.get();
                if (done) {
                    debugTask = null;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void startDebugTask(Supplier<Boolean> debugTask) {
        if (!this.debugMode) {
            broadcaster.apply("First you must enabled debug mode on the flag via the /qtdebug <POS> command", null);
            return;
        }
        this.debugTask = debugTask;
    }

    @Override
    public void toggleDebugMode() {
        this.debugMode = !this.debugMode;
        broadcaster.apply("message.debug_mode", this.debugMode ? "enabled" : "disabled");
    }


    @Override
    public boolean isCacheEnabled() {
        return !cacheOff;
    }
}
