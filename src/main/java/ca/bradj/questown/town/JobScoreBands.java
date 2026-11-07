package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Which scored jobs a villager may pick from, given the two acceptance thresholds. */
final class JobScoreBands {

    private JobScoreBands() {
    }

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

    /** An {@code always_consider} job skips the supply check, so it scores between the bands: picked when nothing better is ready. */
    static double alwaysConsiderScore(double preferred, double min) {
        return (preferred + min) / 2;
    }
}
