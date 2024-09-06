package ca.bradj.questown.logic;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class PredicateCollection<OUTER, INNER> implements IPredicateCollection<OUTER> {
    @SuppressWarnings("rawtypes")
    private static final IPredicateCollection EMPTY = new IPredicateCollection() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean test(Object o) {
            return false;
        }
    };
    private final IPredicateCollection<INNER> inner;
    private final Predicate<IPredicateCollection<INNER>> emptyCheck;
    private final BiPredicate<IPredicateCollection<INNER>, OUTER> test;
    private final String description;

    private PredicateCollection(
            IPredicateCollection<INNER> toWrap,
            Predicate<IPredicateCollection<INNER>> isEmpty,
            BiPredicate<IPredicateCollection<INNER>, OUTER> test,
            String description
    ) {
        this.inner = toWrap;
        this.emptyCheck = isEmpty;
        this.test = test;
        this.description = description;
    }

    public static <S, T> PredicateCollection<S, T> wrap(
            IPredicateCollection<T> toWrap,
            Predicate<IPredicateCollection<T>> isEmpty,
            BiPredicate<IPredicateCollection<T>, S> test,
            String description
    ) {
        return new PredicateCollection<>(toWrap, isEmpty, test, description);
    }

    public static <T> PredicateCollection<T, ?> empty(String msg) {
        //noinspection unchecked
        return new PredicateCollection<T, Object>(EMPTY, IPredicateCollection::isEmpty, Predicate::test, msg);
    }

    @Override
    public boolean isEmpty() {
        return emptyCheck.test(inner);
    }

    @Override
    public boolean test(OUTER t) {
        return test.test(inner, t);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", description, inner);
    }
}
