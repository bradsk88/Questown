package ca.bradj.questown.jobs;

import java.util.Map;

public interface EntityInvStateProvider<STATUS> {
    boolean inventoryFull();

    boolean hasNonSupplyItems();

    Map<STATUS, Boolean> getSupplyItemStatus();
}
