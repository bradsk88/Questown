package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import org.jetbrains.annotations.Nullable;

public interface ItemAcceptor<TOWN> {
    @Nullable TOWN tryGiveItem(
            TOWN town,
            MCHeldItem item,
            InventoryFullStrategy inventoryFullStrategy
    );
}
