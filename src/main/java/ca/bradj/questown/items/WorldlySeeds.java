package ca.bradj.questown.items;

import ca.bradj.questown.core.init.ModItemGroup;
import net.minecraft.world.item.Item;

/**
 * "Worldly Seeds" — pure trigger currency for the helper-chicken onboarding arc.
 *
 * <p>v1 behaviour: stackable to 64, no recipe, no right-click handler,
 * not plantable. Existence + container presence is the trigger.
 */
public class WorldlySeeds extends Item {
    public static final String ITEM_ID = "worldly_seeds";

    public WorldlySeeds() {
        super(new Item.Properties().stacksTo(64).tab(ModItemGroup.QUESTOWN_GROUP));
    }
}
