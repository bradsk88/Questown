package ca.bradj.questown.jobs.gatherer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Rolls a table until it has {@code count} items, giving up after {@code maxRolls}. */
public final class LootRolls {

    private LootRolls() {
    }

    public static <T> List<T> roll(int count, int maxRolls, Supplier<List<T>> oneRoll, Predicate<T> isEmpty) {
        List<T> out = new ArrayList<>();
        for (int i = 0; i < maxRolls && out.size() < count; i++) {
            for (T item : oneRoll.get()) {
                if (!isEmpty.test(item)) {
                    out.add(item);
                }
            }
        }
        return out;
    }
}
