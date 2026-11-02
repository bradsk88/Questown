package ca.bradj.questown.jobs.declarative;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** A full-day trip's second roll: its requested items fill empty slots, then replace random unrequested ones. */
public final class RequestedLootPreference {

    private RequestedLootPreference() {
    }

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
