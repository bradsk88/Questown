package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Set;

/**
 * The roots a random Block of Progress job change may roll: roots no townie holds yet, or, when
 * every root is held, every root but the townie's own (r16 #14, dev ruling 2026-10-24). Pinned by
 * {@code RootRollTest}.
 */
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
