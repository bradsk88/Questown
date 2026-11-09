package ca.bradj.questown.jobs.declarative;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Dev diagnostics: reports a villager that has been collecting supplies for a while without taking anything, then repeats while it stays stuck. */
public final class CollectingStallTrace {

    public static final long FIRST_REPORT_TICKS = 1200;
    public static final long REPEAT_TICKS = 6000;

    private long stuckSince = -1;
    private long lastReport = -1;
    private final PositionTrail trail = new PositionTrail((int) FIRST_REPORT_TICKS);

    public void observe(boolean collecting, boolean took, long tick, Supplier<String> describe, Consumer<String> report) {
        observe(collecting, took, tick, null, describe, report, t -> {});
    }

    /**
     * {@code position} (x, y, z, or null when the entity is not loaded) is sampled on every stuck
     * observation; each report is followed by the trail of the last {@link #FIRST_REPORT_TICKS} samples.
     */
    public void observe(
            boolean collecting,
            boolean took,
            long tick,
            Supplier<double[]> position,
            Supplier<String> describe,
            Consumer<String> report,
            Consumer<String> reportTrail
    ) {
        if (!collecting || took) {
            stuckSince = -1;
            lastReport = -1;
            trail.clear();
            return;
        }
        if (position != null) {
            double[] p = position.get();
            if (p != null) {
                trail.record(tick, p[0], p[1], p[2]);
            }
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
        if (!trail.isEmpty()) {
            reportTrail.accept(trail.toString());
        }
    }
}
