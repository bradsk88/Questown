package ca.bradj.questown.logic;

import java.util.function.Predicate;

public class MonoPredicateCollection<T> extends PredicateCollection<T, T> {

    public MonoPredicateCollection(IPredicateCollection<T> toWrap, String description) {
        super(toWrap, IPredicateCollection::isEmpty, Predicate::test, description);
    }
}
