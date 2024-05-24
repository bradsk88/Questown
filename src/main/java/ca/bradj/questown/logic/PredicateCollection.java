package ca.bradj.questown.logic;

import java.util.function.Predicate;

public interface PredicateCollection<ITEM> extends Predicate<ITEM> {
    boolean isEmpty();
}
