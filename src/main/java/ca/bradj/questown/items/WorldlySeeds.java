package ca.bradj.questown.items;

import net.minecraft.world.item.Item;

/** Worldly Seeds. They do nothing yet. */
// TODO: Worldly Seeds moved the helper chicken's lessons on. For its implementation see #409, #410, #411 and #412.
public class WorldlySeeds extends Item {
    public static final String ITEM_ID = "worldly_seeds";

    public WorldlySeeds() {
        super(new Item.Properties().stacksTo(64));
    }
}
