package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import ca.bradj.questown.loader.Loader;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Ingredients {
    private static com.google.gson.JsonElement toJson(Ingredient item) {
        // 1.20.6's CODEC_NONEMPTY rejects an empty ingredient ("Item array cannot be
        // empty"); 1.19.2's built-in Ingredient.toJson() produced {"items":[]}. Return
        // that empty-items shape so isTag/getName/toString fall through the way the
        // golden does (isTag -> false) instead of throwing and taking the whole
        // skills-pane card render down (putRequestedItemFirst -> isTag).
        if (item.isEmpty()) {
            com.google.gson.JsonObject empty = new com.google.gson.JsonObject();
            empty.add("items", new com.google.gson.JsonArray());
            return empty;
        }
        return Ingredient.CODEC_NONEMPTY.encode(item, com.mojang.serialization.JsonOps.INSTANCE, com.mojang.serialization.JsonOps.INSTANCE.empty()).getOrThrow();
    }

    public static Component getName(Ingredient item) {
        JsonElement j = toJson(item);
        String tag = getTag(j);
        if (tag != null) {
            return Compat.translatable("#" + tag);
        }
        if (j.isJsonObject() && j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            Item i = Loader.itemFromId(new ResourceLocation(tKey));
            if (i != null) {
                return i.getName(i.getDefaultInstance());
            }
        }
        return Compat.translatable("this");
    }

    public static WorkRequest asWorkRequest(Ingredient item) {
        JsonElement j = toJson(item);
        String tag = getTag(j);
        if (tag != null) {
            return WorkRequest.of(new TagKey<>(Registries.ITEM, new ResourceLocation(tag)));
        }
        if (j.isJsonObject() && j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            Item i = Loader.itemFromId(new ResourceLocation(tKey));
            if (i != null) {
                return WorkRequest.of(i);
            }
        }
        throw new IllegalArgumentException("Ingredient has no item or tag");
    }

    public static Ingredient fromString(String block) {
        if (block.startsWith("#")) {
            return Ingredient.of(TagKey.create(
                    Registries.ITEM,
                    new ResourceLocation(block.replace("#", ""))
            ));
        }
        return Ingredient.of(Loader.itemFromId(new ResourceLocation(block)));
    }

    public static String toString(Ingredient item) {
        JsonElement j = toJson(item);
        String tag = getTag(j);
        if (tag != null) {
            String tKey = "#" + tag;
            return tKey;
        }
        if (j.isJsonObject() && j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            return tKey;
        }
        throw new IllegalArgumentException("Ingredient must have tag or item");
    }

    public static @Nullable String getTag(Ingredient ing) {
        JsonElement j = toJson(ing);
        return getTag(j);
    }

    private static @Nullable String getTag(JsonElement j) {
        if (!j.isJsonObject()) {
            return null;
        }
        if (!j.getAsJsonObject().has("tag")) {
            return null;
        }
        return j.getAsJsonObject().get("tag").getAsString();
    }

    /**
     * A rotating representative item for an ingredient (one per second, as the "needs" row cycles),
     * or null when none matches. Used both by {@link #render} and the townie need bubble.
     */
    public static @Nullable ItemStack representativeItemStack(Ingredient ing) {
        ItemStack[] matchingStacks = ing.getItems();
        if (matchingStacks.length == 0) {
            return null;
        }
        int curSeconds = (int) (System.currentTimeMillis() / 1000);
        return matchingStacks[curSeconds % matchingStacks.length];
    }

    public static @Nullable ItemStack render(
            GuiGraphics gui,
            Ingredient ing,
            int iconX,
            int y
    ) {
        ItemStack itemStack = representativeItemStack(ing);
        if (itemStack == null) {
            return null;
        }
        Compat.renderItemAndDecorations(gui, itemStack, iconX, y + 1);
        return itemStack;
    }

    // This one is better than Minecraft's, because it preserves the tag ID
    public static Ingredient fromNetwork(FriendlyByteBuf buffer) {
        return fromString(buffer.readUtf());
    }

    // This one is better than Minecraft's, because it preserves the tag ID
    public static void toNetwork(
            // TODO: Lint against usage of Minecraft functions
            Ingredient requested,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(toString(requested));
    }

    public static Ingredient fromRL(ResourceLocation value) {
        @Nullable Item l = Loader.itemFromId(value);
        return Ingredient.of(l);
    }

    /**
     * Resolve a ResourceLocation to an ingredient, item-first: a registered item id yields
     * the item ingredient; anything else (a tag id, or an unknown id) yields a tag
     * ingredient. A tag ingredient for an unregistered tag simply matches nothing, so a
     * missing id fails safe instead of throwing.
     */
    public static Ingredient asIngredient(ResourceLocation value) {
        Item asItem = Loader.itemFromId(value);
        if (asItem != null) {
            return Ingredient.of(asItem);
        }
        return Ingredient.of(TagKey.create(Registries.ITEM, value));
    }

    public static List<Ingredient> fromItems(ImmutableList<ItemStack> result) {
        return result.stream().filter(v -> !v.isEmpty()).map(Ingredient::of).toList();
    }

    public static boolean equal(
            Ingredient z,
            Ingredient requestedItem
    ) {
        return toString(z).equals(toString(requestedItem));
    }

    public static boolean isTag(Ingredient requestedItem) {
        return getTag(requestedItem) != null;
    }
}
