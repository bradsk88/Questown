package ca.bradj.questown.jobs;

import java.util.function.Function;

public interface StatusListener {
    Runnable jobChanged(Function<StatusListener, Runnable> listenToNewJob);
    void statusChanged(IStatus<?> newStatus);
}
