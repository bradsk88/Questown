package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class TagsInit {

    public static class Items {

        public static final TagKey<Item> VILLAGER_FOOD = createTag("villager_food");
        public static final TagKey<Item> VILLAGER_RAW_FOOD = createTag("villager_raw_food");
        public static final TagKey<Item> AXES = createTag("axes");
        public static final TagKey<Item> PICKAXES = createTag("pickaxes");
        public static final TagKey<Item> SHOVELS = createTag("shovels");
        public static final TagKey<Item> FISHING_RODS = createTag("fishing_rods");
        public static final TagKey<Item> HOES = createTag("hoes");
        public static final TagKey<Item> LIGHT_SOURCES = createTag("light_sources");
        public static final TagKey<Item> LANTERNS = createTag("lanterns");
        public static final TagKey<Item> COMPOSTABLE = createTag("compostable");
        public static final TagKey<Item> JOB_BOARD_INPUTS = createTag("job_board_inputs");

        // BLOCKS
        public static final TagKey<Block> TILLABLES = createBlockTag("tillables");

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(new ResourceLocation(Questown.MODID, name));
        }

        private static TagKey<Block> createBlockTag(String name) {
            return BlockTags.create(new ResourceLocation(Questown.MODID, name));
        }
    }
}
