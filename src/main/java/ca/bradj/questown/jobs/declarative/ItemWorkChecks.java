package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.logic.PredicateCollection;
import org.jetbrains.annotations.Nullable;

public @Nullable interface ItemWorkChecks<EXTRA, HELD_ITEM, TOWN_ITEM> extends WorkChecks<EXTRA, TOWN_ITEM> {
    @Nullable Integer getQuantityForStep(int i, @Nullable Integer orDefault);

    @Nullable PredicateCollection<HELD_ITEM, HELD_ITEM> getIngredientsForStep(int i);
}
