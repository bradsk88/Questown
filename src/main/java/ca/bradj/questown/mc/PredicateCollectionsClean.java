package ca.bradj.questown.mc;

import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;

import java.util.function.Function;
import java.util.function.Predicate;

public class PredicateCollectionsClean {

    public static <TOWN, HELD> PredicateCollection<TOWN, ?> townify(
            PredicateCollection<HELD, ?> v,
            Function<TOWN, HELD> convert
    ) {
        return PredicateCollection.wrap(
                new IPredicateCollection<TOWN>() {
                    @Override
                    public boolean isEmpty() {
                        return v.isEmpty();
                    }

                    @Override
                    public boolean test(TOWN itemStack) {
                        return v.test(convert.apply(itemStack));
                    }

                    @Override
                    public String toString() {
                        return v.toString();
                    }
                },
                IPredicateCollection::isEmpty,
                Predicate::test,
                "Town-as-Held"
        );
    }
}
