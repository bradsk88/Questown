package ca.bradj.questown.items;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GathererMap extends Item {
    public static final String ITEM_ID = "gatherer_map";

    public GathererMap() {
        super(Questown.DEFAULT_ITEM_PROPS.stacksTo(1));
    }

    public static @Nullable ResourceLocation getBiome(ItemStack itemStack) {
        CompoundTag tag = itemStack.getOrCreateTag();
        if (QTNBT.contains(tag, "biome")) {
            return QTNBT.getResourceLocation(tag, "biome");
        }
        return null;
    }

    public static @Nullable ResourceLocation computeBiome(ImmutableList<MCHeldItem> items) {
        ResourceLocation biome = null;
        for (MCHeldItem item : items) {
            if (item.get().get().equals(ItemsInit.GATHERER_MAP.get())) {
                biome = GathererMap.getBiome(item.get().toItemStack());
                if (biome == null) {
                    QT.JOB_LOGGER.error("No biome tag on gatherer map. Ignoring");
                    continue;
                }
                break;
            }
        }
        return biome;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> tooltips, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, tooltips, p_41424_);
        ResourceLocation biome = getBiome(p_41421_);
        String biomeName = "(none)";
        if (biome != null) {
            biomeName = biome.toString();
        }
        tooltips.add(new TextComponent("Biome: " + biomeName));
    }
}
