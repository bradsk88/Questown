package ca.bradj.questown.jobs.declarative;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Diagnostic tripwire (2026-10-21 playtest: after a sleep, hunters stood in COLLECTING_SUPPLIES
 * for 17+ minutes; not reproduced by autotest). Once a villager has been collecting supplies for
 * {@link #FIRST_REPORT_TICKS} without taking anything, report its state once, then every
 * {@link #REPEAT_TICKS} while it stays stuck. Taking something or leaving the status resets it.
 */
public final class CollectingStallTrace {

    public static final long FIRST_REPORT_TICKS = 1200;
    public static final long REPEAT_TICKS = 6000;

    private long stuckSince = -1;
    private long lastReport = -1;

    public void observe(boolean collecting, boolean took, long tick, Supplier<String> describe, Consumer<String> report) {
        if (!collecting || took) {
            stuckSince = -1;
            lastReport = -1;
            return;
        }
        if (stuckSince < 0) {
            stuckSince = tick;
            return;
        }
        long stuck = tick - stuckSince;
        if (stuck < FIRST_REPORT_TICKS) {
            return;
        }
        if (lastReport >= 0 && tick - lastReport < REPEAT_TICKS) {
            return;
        }
        lastReport = tick;
        report.accept("stuck " + stuck + " ticks: " + describe.get());
    }
}
