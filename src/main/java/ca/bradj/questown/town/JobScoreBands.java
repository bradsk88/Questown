package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Forge-free half of {@link TownPossibleWork}'s job selection: which scored jobs a
 * villager may pick from, given the two acceptance thresholds from config.
 */
final class JobScoreBands {

    private JobScoreBands() {
    }

    /** The preferred band if any job reaches it, else the minimum band. */
    static <J> ImmutableList<J> band(
            List<J> jobs,
            ToDoubleFunction<J> score,
            double preferred,
            double min
    ) {
        List<J> top = jobs.stream().filter(v -> score.applyAsDouble(v) > preferred).toList();
        if (!top.isEmpty()) {
            return ImmutableList.copyOf(top);
        }
        return jobs.stream().filter(v -> score.applyAsDouble(v) > min).collect(ImmutableList.toImmutableList());
    }

    /**
     * An {@code always_consider} job skips the supply gate, so it has no real score. It sits
     * midway between the bands: always a candidate when nothing better is ready, never tied
     * with a job that can actually be done (dev ruling 2026-10-23, the stock-job coin flip).
     */
    static double alwaysConsiderScore(double preferred, double min) {
        return (preferred + min) / 2;
    }
}
