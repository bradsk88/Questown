package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.gson.JsonElement;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class Ingredients {
    public static Component getName(Ingredient item) {
        JsonElement j = item.toJson();
        String tag = getTag(j);
        if (tag != null) {
            return new TranslatableComponent("#" + tag);
        }
        if (j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tKey));
            if (i != null) {
                return i.getName(i.getDefaultInstance());
            }
        }
        return new TranslatableComponent("this");
    }

    public static WorkRequest asWorkRequest(Ingredient item) {
        JsonElement j = item.toJson();
        String tag = getTag(j);
        if (tag != null) {
            return WorkRequest.of(new TagKey<>(Registry.ITEM_REGISTRY, new ResourceLocation("#" + tag)));
        }
        if (j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tKey));
            if (i != null) {
                return WorkRequest.of(i);
            }
        }
        throw new IllegalArgumentException("Ingredient has no item or tag");
    }

    public static Ingredient fromString(String block) {
        if (block.startsWith("#")) {
            return Ingredient.of(TagKey.create(
                    Registry.ITEM_REGISTRY,
                    new ResourceLocation(block.replace("#", ""))
            ));
        }
        return Ingredient.of(ForgeRegistries.ITEMS.getValue(new ResourceLocation(block)));
    }

    public static String toString(Ingredient item) {
        JsonElement j = item.toJson();
        String tag = getTag(j);
        if (tag != null) {
            String tKey = "#" + tag;
            return tKey;
        }
        if (j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            return tKey;
        }
        throw new IllegalArgumentException("Ingredient must have tag or item");
    }

    public static @Nullable String getTag(Ingredient ing) {
        JsonElement j = ing.toJson();
        return getTag(j);
    }

    private static @Nullable String getTag(JsonElement j) {
        if (!j.getAsJsonObject().has("tag")) {
            return null;
        }
        return j.getAsJsonObject().get("tag").getAsString();
    }

    public static @Nullable ItemStack render(
            ItemRenderer itemRenderer,
            Ingredient ing,
            int iconX,
            int y
    ) {
        int curSeconds = (int) (System.currentTimeMillis() / 1000);
        ItemStack[] matchingStacks = ing.getItems();
        if (matchingStacks.length > 0) {
            ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
            itemRenderer.renderAndDecorateItem(itemStack, iconX, y + 1);
            return itemStack;
        }
        return null;
    }
}
