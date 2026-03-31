package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.SupplyChecks;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

public interface Dependencies4<HELD_ITEM> {
    Supplier<ImmutableList<HELD_ITEM>> getJournalItemsSupplier();

    SupplyChecks<HELD_ITEM> asChecks();

    int getMaxState();
}
