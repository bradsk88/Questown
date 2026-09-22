package ca.bradj.questown.town.econ;

/**
 * Tracks a per-window total derived from a monotonically non-decreasing
 * cumulative counter. Each call to {@link #next} records the counter's current
 * value and returns the delta since the previous call. The first call (with
 * {@code initial = 0}) returns the cumulative total itself; subsequent calls
 * return only the amount added since the last call.
 *
 * <p>Used by {@link NoMCEconomics#getUnmetNeedsWindowTotal} to present the
 * unmet-needs count as a bounded per-window figure rather than the
 * cumulative-since-town-start aggregate.
 *
 * <p>Pure: no Minecraft or Forge types, so it is unit-testable in a plain JUnit
 * run without loading a {@code MinecraftServer} or {@code ForgeConfigSpec}.
 */
public final class WindowTotal {

    private long last;

    public WindowTotal(long initialCumulative) {
        this.last = initialCumulative;
    }

    /**
     * Returns {@code newCumulative - last} and advances this window's baseline
     * to {@code newCumulative}. Call repeatedly to walk successive windows over
     * the same cumulative counter.
     */
    public long next(long newCumulative) {
        long window = newCumulative - last;
        last = newCumulative;
        return window;
    }
}
