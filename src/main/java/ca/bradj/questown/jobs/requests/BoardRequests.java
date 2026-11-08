package ca.bradj.questown.jobs.requests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** The job board's requests, and which are paused because storage holds {@link #PAUSE_AT} of the item. A paused request stays on the board, but nobody works on it until stock drops. */
public record BoardRequests(
        ImmutableList<WorkRequest> all,
        ImmutableSet<WorkRequest> paused
) {

    public static final int PAUSE_AT = 8;

    public static BoardRequests unpaused(ImmutableList<WorkRequest> all) {
        return new BoardRequests(all, ImmutableSet.of());
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public ImmutableList<WorkRequest> toWork() {
        return all.stream().filter(r -> !paused.contains(r)).collect(ImmutableList.toImmutableList());
    }

    /** The requests a job may answer. A job that makes nothing (fuel, stock, display) still answers paused ones, since it uses stored items. */
    public ImmutableList<WorkRequest> toWorkFor(boolean jobMakesNothing) {
        return jobMakesNothing ? all : toWork();
    }

    /** The requests storage holds at least {@link #PAUSE_AT} of. A request an open quest still wants never pauses. */
    public static <R> ImmutableSet<R> stocked(
            Collection<R> requests,
            ToIntFunction<R> inStorage,
            Predicate<R> exempt
    ) {
        return stocked(requests, inStorage, exempt, r -> PAUSE_AT);
    }

    /** As above, at each request's own limit. */
    public static <R> ImmutableSet<R> stocked(
            Collection<R> requests,
            ToIntFunction<R> inStorage,
            Predicate<R> exempt,
            ToIntFunction<R> limit
    ) {
        return requests.stream()
                .filter(r -> !exempt.test(r))
                .filter(r -> inStorage.applyAsInt(r) >= limit.applyAsInt(r))
                .collect(ImmutableSet.toImmutableSet());
    }
}
