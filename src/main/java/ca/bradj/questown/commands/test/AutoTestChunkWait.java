package ca.bradj.questown.commands.test;

import java.util.function.Consumer;

/** Holds the suite until the force-loaded test chunks tick, or a wall-clock timeout passes (then the suite starts anyway). */
final class AutoTestChunkWait {

    private final long timeoutMs;
    private long startMs = -1;
    private int polls = 0;
    private boolean done = false;

    AutoTestChunkWait(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    boolean isDone() {
        return done;
    }

    /** True once the suite may start. Logs once, on readiness or on timeout. */
    boolean poll(boolean ticking, long nowMs, Consumer<String> info, Consumer<String> error) {
        if (done) {
            return true;
        }
        if (startMs < 0) {
            startMs = nowMs;
        }
        long waited = nowMs - startMs;
        if (ticking) {
            done = true;
            info.accept("Force-loaded chunks ticking after " + polls + " ticks (" + waited + " ms)");
            return true;
        }
        polls++;
        if (waited >= timeoutMs) {
            done = true;
            error.accept("Force-loaded chunks still not ticking after " + polls + " ticks (" + waited + " ms); starting anyway");
            return true;
        }
        return false;
    }
}
