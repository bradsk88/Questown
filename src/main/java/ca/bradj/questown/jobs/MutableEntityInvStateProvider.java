package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.Map;

public class MutableEntityInvStateProvider<HELD_ITEM> implements EntityInvStateProvider<Integer> {

    private ImmutableCollection<HELD_ITEM> items = ImmutableList.of();
    private final DefaultInventoryStateProvider<HELD_ITEM> delegate = new DefaultInventoryStateProvider<>(
            () -> this.items
    );

    public static <HELD_ITEM> MutableEntityInvStateProvider<HELD_ITEM> withInitialItems(ImmutableList<HELD_ITEM> items) {
        return new MutableEntityInvStateProvider<>();
    }

    @Override
    public boolean inventoryFull() {
        return false;
    }

    @Override
    public boolean hasNonSupplyItems() {
        return false;
    }

    @Override
    public Map<Integer, Boolean> getSupplyItemStatus() {
        return null;
    }
}
