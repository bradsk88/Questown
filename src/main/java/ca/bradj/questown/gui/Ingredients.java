package ca.bradj.questown.gui;

import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

public class Ingredients {
    public static Component getName(Ingredient item) {
        JsonElement j = item.toJson();
        if (j.getAsJsonObject().has("tag")) {
            String tKey = "#" + j.getAsJsonObject().get("tag").getAsString();
            return new TranslatableContents(tKey);
        }
        if (j.getAsJsonObject().has("item")) {
            String tKey = j.getAsJsonObject().get("item").getAsString();
            Item i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tKey));
            if (i != null) {
                return i.getName(i.getDefaultInstance());
            }
        }
        return new TranslatableContents("this");
    }
}
