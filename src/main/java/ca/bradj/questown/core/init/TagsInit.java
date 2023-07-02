package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class TagsInit {

    public static class Items {

        public static final TagKey<Item> VILLAGER_FOOD = createTag("villager_food");


        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(new ResourceLocation(Questown.MODID, name));
        }
    }
}
