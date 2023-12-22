package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkSpot;

public interface ItemWI<POS, EXTRA, TOWN> {
    TOWN tryInsertIngredients(
            EXTRA extra,
            WorkSpot<Integer,POS> workSpot
    );
}
