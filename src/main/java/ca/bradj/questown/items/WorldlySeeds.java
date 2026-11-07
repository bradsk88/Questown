package ca.bradj.questown.items;

import net.minecraft.world.item.Item;

/** Worldly Seeds: having them in a town chest moves the helper chicken's lessons on. They do nothing else. */
public class WorldlySeeds extends Item {
    public static final String ITEM_ID = "worldly_seeds";

    public WorldlySeeds() {
        super(new Item.Properties().stacksTo(64));
    }
}
