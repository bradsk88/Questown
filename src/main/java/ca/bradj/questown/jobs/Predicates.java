package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.function.Function;

public class Predicates {
    public static <ITEM> NoisyPredicate<ITEM> applyWrapping(
            Collection<? extends Function<NoisyPredicate<ITEM>, NoisyPredicate<ITEM>>> wrappers,
            NoisyPredicate<ITEM> originalCheck
    ) {
        NoisyPredicate<ITEM> check = originalCheck;
        for (Function<NoisyPredicate<ITEM>, NoisyPredicate<ITEM>> wrapper : wrappers) {
            check = wrapper.apply(check);
        }
        return check;
    }
}
