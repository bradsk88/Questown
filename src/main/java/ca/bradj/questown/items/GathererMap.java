package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
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

    public static @Nullable ResourceLocation computeBiome(ImmutableList<MCHeldItem> items) {
        ResourceLocation biome = null;
        // TODO[ASAP]: Bring back
//        for (MCHeldItem item : items) {
//            if (item.get().get().equals(ItemsInit.GATHERER_MAP.get())) {
//                biome = GathererMap.getBiome(item.get().toItemStack());
//                if (biome == null) {
//                    QT.JOB_LOGGER.error("No biome tag on gatherer map. Ignoring");
//                    continue;
//                }
//                break;
//            }
//        }
        return biome;
    }
}
