package ca.bradj.questown.jobs.declarative;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * A full-day leaver trip's second pass: after the
 * trip's normal roll, a second roll's requested items (anything the job board asks for) fill the
 * inventory's empty slots, then each replaces a random non-requested item. Non-requested items
 * from the second roll are dropped, and once every slot holds a requested item the rest are too.
 * Registry-free so it can be unit-tested; the world interactions supply the rolls and the board.
 */
public final class RequestedLootPreference {

    private RequestedLootPreference() {
    }

    /**
     * @param randomIndex given a bound n, returns an index in [0, n)
     */
    public static <T> List<T> prefer(
            List<T> rolled,
            List<T> secondRoll,
            Predicate<T> requested,
            int capacity,
            IntUnaryOperator randomIndex
    ) {
        List<T> out = new ArrayList<>(rolled.subList(0, Math.min(rolled.size(), capacity)));
        for (T item : secondRoll) {
            if (!requested.test(item)) {
                continue;
            }
            if (out.size() < capacity) {
                out.add(item);
                continue;
            }
            List<Integer> replaceable = new ArrayList<>();
            for (int i = 0; i < out.size(); i++) {
                if (!requested.test(out.get(i))) {
                    replaceable.add(i);
                }
            }
            if (replaceable.isEmpty()) {
                break;
            }
            out.set(replaceable.get(randomIndex.applyAsInt(replaceable.size())), item);
        }
        return out;
    }
}
