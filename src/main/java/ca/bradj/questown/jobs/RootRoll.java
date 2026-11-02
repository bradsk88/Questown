package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Set;

/** The jobs a random Block of Progress job change can roll: ones no villager has, or else any but the villager's own. */
public final class RootRoll {

    private RootRoll() {
    }

    public static ImmutableSet<JobID> candidates(
            Collection<JobID> allRoots,
            JobID current,
            Set<String> heldRoots
    ) {
        ImmutableSet<JobID> others = allRoots.stream()
                .filter(r -> !current.sameRoot(r))
                .collect(ImmutableSet.toImmutableSet());
        ImmutableSet<JobID> unheld = others.stream()
                .filter(r -> !heldRoots.contains(r.rootId()))
                .collect(ImmutableSet.toImmutableSet());
        if (unheld.isEmpty()) {
            return others;
        }
        return unheld;
    }
}
