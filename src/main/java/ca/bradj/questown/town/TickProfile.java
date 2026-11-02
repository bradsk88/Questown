package ca.bradj.questown.town;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dev-only: recent flag-tick durations in microseconds, with the slow tail. Off unless a perf scenario or the profile command turns it on. */
public final class TickProfile {

    public static final TickProfile INSTANCE = new TickProfile();

    private static final int MAX_SAMPLES = 200_000;

    private final List<Integer> micros = new ArrayList<>();
    private final java.util.Map<String, List<Integer>> phases = new java.util.LinkedHashMap<>();
    /** Whole-server-tick samples, kept apart from the flag-tick ones: a server stall can happen while the flag tick is fast. */
    private final List<Integer> serverMicros = new ArrayList<>();
    private boolean enabled = false;

    private TickProfile() {
    }

    /** Times {@code body} into a named bucket. */
    public void phase(String name, Runnable body) {
        if (!enabled) {
            body.run();
            return;
        }
        long start = System.nanoTime();
        try {
            body.run();
        } finally {
            recordPhase(name, System.nanoTime() - start);
        }
    }

    private synchronized void recordPhase(String name, long nanos) {
        List<Integer> bucket = phases.computeIfAbsent(name, k -> new ArrayList<>());
        if (bucket.size() < MAX_SAMPLES) {
            bucket.add((int) (nanos / 1000));
        }
    }

    public synchronized List<String> describePhases() {
        List<String> out = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Integer>> e : phases.entrySet()) {
            List<Integer> sorted = new ArrayList<>(e.getValue());
            if (sorted.isEmpty()) {
                continue;
            }
            Collections.sort(sorted);
            long total = 0;
            for (int v : sorted) {
                total += v;
            }
            out.add(String.format(
                    "  %-22s n=%-6d avg=%6.0fus p95=%6dus max=%6dus  (%.0f%% of all tick time)",
                    e.getKey(), sorted.size(), (double) total / sorted.size(),
                    percentile(sorted, 95), sorted.get(sorted.size() - 1),
                    totalMicros() == 0 ? 0.0 : 100.0 * total / totalMicros()
            ));
        }
        return out;
    }

    private synchronized long totalMicros() {
        long total = 0;
        for (int v : micros) {
            total += v;
        }
        return total;
    }

    /** How much of the worst server tick the flag tick accounts for, and how much lies outside it. */
    public synchronized List<String> describeStallAttribution() {
        List<String> out = new ArrayList<>();
        if (serverMicros.isEmpty() || micros.isEmpty()) {
            out.add("  [stall] no server/flag-tick samples yet; run after enabling and waiting.");
            return out;
        }
        int serverMax = maxOf(serverMicros);
        int flagMax = maxOf(micros);
        long phaseMaxSum = 0;
        for (List<Integer> bucket : phases.values()) {
            if (!bucket.isEmpty()) {
                phaseMaxSum += maxOf(bucket);
            }
        }
        long outsideFlag = Math.max(0, serverMax - flagMax);
        out.add(String.format(
                "  [stall] server-tick max = %dms | flag-tick max = %dms | "
                        + "%dms is outside the flag tick (chunk save / GC / another block entity)",
                serverMax / 1000, flagMax / 1000, outsideFlag / 1000
        ));
        out.add(
                String.format(
                        "  [stall] phase maxes sum to %dms (<= flag-tick max %dms; the gap is "
                                + "untimed flag-tick work: villagers, economics)",
                        phaseMaxSum / 1000, flagMax / 1000
                )
        );
        return out;
    }

    private static int maxOf(List<Integer> samples) {
        int max = 0;
        for (int v : samples) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void enable() {
        enabled = true;
        micros.clear();
        serverMicros.clear();
    }

    public synchronized void disable() {
        enabled = false;
        micros.clear();
        serverMicros.clear();
    }

    public synchronized void reset() {
        micros.clear();
        serverMicros.clear();
    }

    public synchronized void record(long nanos) {
        if (!enabled || micros.size() >= MAX_SAMPLES) {
            return;
        }
        micros.add((int) (nanos / 1000));
    }

    /** Records one whole-server-tick sample. */
    public synchronized void recordServer(long nanos) {
        if (!enabled || serverMicros.size() >= MAX_SAMPLES) {
            return;
        }
        serverMicros.add((int) (nanos / 1000));
    }

    public synchronized Snapshot snapshot() {
        if (micros.isEmpty()) {
            return new Snapshot(0, 0, 0, 0, 0, 0);
        }
        List<Integer> sorted = new ArrayList<>(micros);
        Collections.sort(sorted);
        long total = 0;
        for (int v : sorted) {
            total += v;
        }
        return new Snapshot(
                sorted.size(),
                (double) total / sorted.size(),
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted.get(sorted.size() - 1)
        );
    }

    /** The whole-server-tick samples. */
    public synchronized Snapshot snapshotServer() {
        if (serverMicros.isEmpty()) {
            return new Snapshot(0, 0, 0, 0, 0, 0);
        }
        List<Integer> sorted = new ArrayList<>(serverMicros);
        Collections.sort(sorted);
        long total = 0;
        for (int v : sorted) {
            total += v;
        }
        return new Snapshot(
                sorted.size(),
                (double) total / sorted.size(),
                percentile(sorted, 50),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted.get(sorted.size() - 1)
        );
    }

    private static int percentile(List<Integer> sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    /** All in microseconds; a 20 TPS server has 50,000 per tick. */
    public record Snapshot(int count, double avgMicros, int p50Micros, int p95Micros, int p99Micros,
                           int maxMicros) {
        public String describe() {
            return String.format(
                    "n=%d avg=%.0fus p50=%dus p95=%dus p99=%dus max=%dus (server tick budget = 50000us)",
                    count, avgMicros, p50Micros, p95Micros, p99Micros, maxMicros
            );
        }
    }
}
