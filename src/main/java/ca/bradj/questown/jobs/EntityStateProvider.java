package ca.bradj.questown.jobs;

import java.util.Map;

public interface EntityStateProvider {
    boolean inventoryFull();

    boolean hasNonSupplyItems();

    Map<GathererJournal.Status, Boolean> getSupplyItemStatus();

    boolean hasItems();
}
