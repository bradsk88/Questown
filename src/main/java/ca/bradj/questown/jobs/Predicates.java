package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

public class Predicates {
    public static <ITEM> Predicate<ITEM> applyWrapping(
            Collection<? extends Function<Predicate<ITEM>, Predicate<ITEM>>> wrappers,
            Predicate<ITEM> originalCheck
    ) {
        if (originalCheck == null) {
            return (item) -> false;
        }
        Predicate<ITEM> check = originalCheck;
        for (Function<Predicate<ITEM>, Predicate<ITEM>> wrapper : wrappers) {
            check = wrapper.apply(check);
        }
        return check;
    }
}
