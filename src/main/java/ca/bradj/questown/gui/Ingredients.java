package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.gson.JsonElement;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

public class Ingredients {
    public static Component getName(Ingredient item) {
        JsonElement j = item.toJson();
        if (j.getAsJsonObject().has("tag")) {
            String tKey = "#" + j.getAsJsonObject().get("tag").getAsString();
            return new TranslatableComponent(tKey);
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
        if (j.getAsJsonObject().has("tag")) {
            String tKey = "#" + j.getAsJsonObject().get("tag").getAsString();
            return WorkRequest.of(new TagKey<>(Registry.ITEM_REGISTRY, new ResourceLocation(tKey)));
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
}
