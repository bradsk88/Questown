package ca.bradj.questown.jobs.declarative;

/** Dev diagnostics: a villager's exact position on each of its last {@code capacity} samples, printed as runs of samples that did not move. */
public final class PositionTrail {

    /** Movement below this (blocks, on any axis) counts as standing still. */
    static final double STILL = 0.01;

    private final long[] ticks;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private int start = 0;
    private int size = 0;

    public PositionTrail(int capacity) {
        ticks = new long[capacity];
        xs = new double[capacity];
        ys = new double[capacity];
        zs = new double[capacity];
    }

    public void record(long tick, double x, double y, double z) {
        int i = (start + size) % ticks.length;
        if (size == ticks.length) {
            start = (start + 1) % ticks.length;
        } else {
            size++;
        }
        ticks[i] = tick;
        xs[i] = x;
        ys[i] = y;
        zs[i] = z;
    }

    public void clear() {
        start = 0;
        size = 0;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** e.g. {@code from tick 100: +0..+37 (0.50,64.00,0.50); +38 (0.62,64.00,0.61)}. */
    @Override
    public String toString() {
        if (size == 0) {
            return "empty";
        }
        long first = ticks[start];
        StringBuilder b = new StringBuilder("from tick ").append(first).append(":");
        int run = 0;
        for (int n = 1; n <= size; n++) {
            int r = (start + run) % ticks.length;
            if (n < size) {
                int i = (start + n) % ticks.length;
                if (Math.abs(xs[i] - xs[r]) < STILL && Math.abs(ys[i] - ys[r]) < STILL && Math.abs(zs[i] - zs[r]) < STILL) {
                    continue;
                }
            }
            int last = (start + n - 1) % ticks.length;
            b.append(run == 0 ? " " : "; ").append('+').append(ticks[r] - first);
            if (n - 1 > run) {
                b.append("..+").append(ticks[last] - first);
            }
            b.append(String.format(java.util.Locale.ROOT, " (%.2f,%.2f,%.2f)", xs[r], ys[r], zs[r]));
            run = n;
        }
        return b.toString();
    }
}
