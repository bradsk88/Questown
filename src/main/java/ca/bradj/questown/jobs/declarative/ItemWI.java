package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.logic.PredicateCollection;

public interface ItemWI<POS, EXTRA, TOWN, ITEM> {
    InsertResult<TOWN, ITEM> tryInsertIngredients(
            EXTRA extra,
            PredicateCollection<ITEM, ITEM> ingredientsForStep,
            WorkedSpot<POS> workSpot
    );
}
