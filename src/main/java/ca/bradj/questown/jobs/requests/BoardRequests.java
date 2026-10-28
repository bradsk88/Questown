package ca.bradj.questown.jobs.requests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The job board's requests, and which of them are paused because town storage already holds
 * {@link #PAUSE_AT} of the item (agent playtest 2026-10-27 #2, dev: "ideally yes"). A paused
 * request stays on the board, so the board is not "empty" (an empty board lets a villager pick
 * any work), but no villager picks work to fill it until the stock drops.
 */
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

    /** The requests a villager may pick work for. */
    public ImmutableList<WorkRequest> toWork() {
        return all.stream().filter(r -> !paused.contains(r)).collect(ImmutableList.toImmutableList());
    }

    /**
     * The requests storage holds at least {@link #PAUSE_AT} of. An exempt request (an open quest
     * still wants the item) never pauses.
     */
    public static <R> ImmutableSet<R> stocked(
            Collection<R> requests,
            ToIntFunction<R> inStorage,
            Predicate<R> exempt
    ) {
        return requests.stream()
                .filter(r -> !exempt.test(r))
                .filter(r -> inStorage.applyAsInt(r) >= PAUSE_AT)
                .collect(ImmutableSet.toImmutableSet());
    }
}
