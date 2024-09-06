package ca.bradj.questown.logic;

import java.util.function.Predicate;

public interface IPredicateCollection<ITEM> extends Predicate<ITEM> {
    boolean isEmpty();
}
