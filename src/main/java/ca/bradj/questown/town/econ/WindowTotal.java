package ca.bradj.questown.town.econ;

/** Turns a growing total into per-window amounts: each {@link #next} returns what was added since the last call. */
public final class WindowTotal {

    private long last;

    public WindowTotal(long initialCumulative) {
        this.last = initialCumulative;
    }

    /** What the total grew by since the last call. */
    public long next(long newCumulative) {
        long window = newCumulative - last;
        last = newCumulative;
        return window;
    }
}
