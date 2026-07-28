package ca.bradj.questown.town;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dev-only rolling record of town-flag tick durations, in microseconds.
 *
 * <p>Exists because the pre-existing {@code TICK_SAMPLING_RATE} profiling reported only a
 * millisecond-resolution <em>average</em>, which cannot see a flag tick at all: the whole server
 * tick budget is 50ms, so a flag tick that costs 400us rounds to 0 and a rare 30ms spike is
 * averaged into invisibility. Stutter is a tail problem, so the tail is what this records.
 *
 * <p>Disabled by default and off the production path — {@link #enable()} is called only by the
 * autotest perf scenario.
 */
public final class TickProfile {

    public static final TickProfile INSTANCE = new TickProfile();

    private static final int MAX_SAMPLES = 200_000;

    private final List<Integer> micros = new ArrayList<>();
    private final java.util.Map<String, List<Integer>> phases = new java.util.LinkedHashMap<>();
    private boolean enabled = false;

    private TickProfile() {
    }

    /**
     * Times {@code body} into a named bucket so a run attributes cost to a phase rather than only
     * reporting a total. Zero overhead when disabled beyond the boolean check.
     */
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

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void enable() {
        enabled = true;
        micros.clear();
    }

    public synchronized void disable() {
        enabled = false;
        micros.clear();
    }

    public synchronized void reset() {
        micros.clear();
    }

    public synchronized void record(long nanos) {
        if (!enabled || micros.size() >= MAX_SAMPLES) {
            return;
        }
        micros.add((int) (nanos / 1000));
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

    private static int percentile(List<Integer> sorted, int pct) {
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    /** All durations in microseconds. A 20 TPS server has a 50_000us budget per server tick. */
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
