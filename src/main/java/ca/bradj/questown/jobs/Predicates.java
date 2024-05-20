package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

public class Predicates {
    public static <ITEM> NoisyPredicate<ITEM> applyWrapping(
            Collection<? extends Function<NoisyPredicate<ITEM>, NoisyPredicate<ITEM>>> wrappers,
            NoisyPredicate<ITEM> originalCheck
    ) {
        if (originalCheck == null) {
            return (item) -> new WithReason<>(false, "Original check is null");
        }
        NoisyPredicate<ITEM> check = originalCheck;
        for (Function<NoisyPredicate<ITEM>, NoisyPredicate<ITEM>> wrapper : wrappers) {
            check = wrapper.apply(check);
        }
        return check;
    }
}
