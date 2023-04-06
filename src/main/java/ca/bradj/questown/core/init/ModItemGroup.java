package ca.bradj.questown.core.init;

import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModItemGroup {

    public static final CreativeModeTab QUESTOWN_GROUP = new CreativeModeTab("eurekaCraftTab") {
        @Override
        public ItemStack makeIcon() {
            return ItemsInit.TOWN_FLAG_BLOCK.get().getDefaultInstance();
        }
    };
}
