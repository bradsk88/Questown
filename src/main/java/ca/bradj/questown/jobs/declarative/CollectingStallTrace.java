package ca.bradj.questown.jobs.declarative;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dev diagnostics: reports a villager that has been collecting supplies for a while without taking anything, then repeats while it stays stuck. */
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
