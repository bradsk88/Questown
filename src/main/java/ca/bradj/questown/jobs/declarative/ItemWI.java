package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkedSpot;

public interface ItemWI<POS, EXTRA, TOWN, ITEM> {
    InsertResult<TOWN, ITEM> tryInsertIngredients(
            EXTRA extra,
            WorkedSpot<POS> workSpot
    );
}
