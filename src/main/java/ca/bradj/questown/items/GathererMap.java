package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class GathererMap extends Item {
    public static final String ITEM_ID = "gatherer_map";

    public GathererMap() {
        super(Questown.DEFAULT_ITEM_PROPS);
    }

    public static @Nullable ResourceLocation getBiome(ItemStack itemStack) {
        if (itemStack.getOrCreateTag().contains("biome")) {
            return new ResourceLocation(itemStack.getOrCreateTag().getString("biome"));
        }
        return null;
    }
}