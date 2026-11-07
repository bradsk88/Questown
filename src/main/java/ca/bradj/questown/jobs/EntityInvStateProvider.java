package ca.bradj.questown.jobs;

import java.util.Map;

public interface EntityInvStateProvider<SUP_CAT> {
    boolean inventoryFull();

    boolean hasNonSupplyItems();

    /** Whether the villager holds work output: an item no state of the job uses, as ingredient or tool. */
    default boolean holdsWorkOutput() {
        return hasNonSupplyItems();
    }

    Map<SUP_CAT, SupplyItemStatus> getSupplyItemStatus();
}
