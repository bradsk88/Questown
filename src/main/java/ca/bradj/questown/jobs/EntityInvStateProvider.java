package ca.bradj.questown.jobs;

import java.util.Map;

public interface EntityInvStateProvider<SUP_CAT> {
    boolean inventoryFull();

    boolean hasNonSupplyItems();

    /**
     * Holding output of the work: an item no state of the job uses, as ingredient or tool. Unlike
     * {@link #hasNonSupplyItems} (ingredients of the states that need input right now), a held
     * tool or a later state's ingredient does not count. Providers without per-state recipes
     * fall back to {@link #hasNonSupplyItems}.
     */
    default boolean holdsWorkOutput() {
        return hasNonSupplyItems();
    }

    Map<SUP_CAT, SupplyItemStatus> getSupplyItemStatus();
}
