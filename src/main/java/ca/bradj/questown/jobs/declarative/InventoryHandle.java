package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.Item;

import java.util.Collection;

public interface InventoryHandle<ITEM extends Item<ITEM>> {
    Collection<ITEM> getItems();

    void set(
            int ii,
            ITEM shrink
    );
}
