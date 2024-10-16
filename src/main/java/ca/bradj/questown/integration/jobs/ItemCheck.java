package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;

import java.util.Collection;

public interface ItemCheck<ITEM> {
    boolean isEmpty(Collection<MCHeldItem> heldItems);

    boolean test(
            Collection<MCHeldItem> heldItems,
            ITEM item
    );
}
