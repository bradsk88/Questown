package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import net.minecraft.world.item.ItemStack;

public class QTNBT {
    public static void putString(
            ItemStack map,
            String key,
            String value
    ) {
        map.getOrCreateTag().putString(String.format("%s_%s", Questown.MODID, key), value);
    }
}
