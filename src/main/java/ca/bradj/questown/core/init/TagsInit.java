package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class TagsInit {

    public static class Items {

        public static final TagKey<Item> VILLAGER_FOOD = createTag("villager_food");
        public static final TagKey<Item> AXES = createTag("axes");
        public static final TagKey<Item> PICKAXES = createTag("pickaxes");
        public static final TagKey<Item> SHOVELS = createTag("shovels");
        public static final TagKey<Item> FISHING_RODS = createTag("fishing_rods");
        public static final TagKey<Item> LIGHT_SOURCES = createTag("light_sources");
        public static final TagKey<Item> LANTERNS = createTag("lanterns");


        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(new ResourceLocation(Questown.MODID, name));
        }
    }
}
