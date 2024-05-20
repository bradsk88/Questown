package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;

import java.util.Map;

public interface EntityInvStateProvider<SUP_CAT> {
    boolean inventoryFull();

    WithReason<Boolean> hasNonSupplyItems(boolean allowCaching);

    Map<SUP_CAT, Boolean> getSupplyItemStatus();
}
