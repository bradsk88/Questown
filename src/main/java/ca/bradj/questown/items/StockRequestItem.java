package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public class StockRequestItem extends Item {
    public static final String ITEM_ID = "stock_request";

    public StockRequestItem() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    // TODO: Hover text
}
