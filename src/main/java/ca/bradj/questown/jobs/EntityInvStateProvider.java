package ca.bradj.questown.jobs;

import java.util.Map;

public interface EntityInvStateProvider<SUP_CAT> {
    boolean inventoryFull();

    boolean hasNonSupplyItems(boolean allowCaching);

    Map<SUP_CAT, Boolean> getSupplyItemStatus();
}
