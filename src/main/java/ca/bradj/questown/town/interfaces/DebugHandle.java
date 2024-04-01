package ca.bradj.questown.town.interfaces;

import java.util.function.Supplier;

public interface DebugHandle extends CacheFilter {
    void toggleCache();

    boolean runDebugTask();

    void startDebugTask(Supplier<Boolean> debugTask);

    void toggleDebugMode();
}
