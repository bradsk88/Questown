package ca.bradj.questown.core;

import ca.bradj.questown.core.init.TagsInit;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import com.google.common.collect.ImmutableList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;

public class RecipeItemConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final String ITEM_WEIGHTS = "Item weights";
    private static final Config defaultItemWeights = Config.of(InMemoryFormat.defaultInstance());
    public static final ForgeConfigSpec.ConfigValue<Config> itemWeights;

    private static final int TORCH_SCORE = RecipeItemScore.canCraftInFourGrid(
            CommonRecipes.TORCH_INGREDIENTS, false
    );

    private static final int CRAFTING_TABLE = RecipeItemScore.canCraftInFourGrid(
            CommonRecipes.CRAFTING_TABLE, false
    );
    private static final int BED = RecipeItemScore.requiresCraftingTable(ImmutableList.of(
            new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
            new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
            new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
            new CraftedResource("planks", 4, ImmutableList.of(
                    new MinedResource("wood", Rarity.EASY_TO_FIND))
            ),
            new CraftedResource("planks", 4, ImmutableList.of(
                    new MinedResource("wood", Rarity.EASY_TO_FIND))
            ),
            new CraftedResource("planks", 4, ImmutableList.of(
                    new MinedResource("wood", Rarity.EASY_TO_FIND))
            )
    ), true);
    private static final int DIRT =
            new MinedResource("dirt", Rarity.ITS_EVERYWHERE).calculateValue();
    private static final int CHEST = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.CHEST,
            true // Boosted because gatherers need storage ASAP
    );

    private static final int FURNACE = RecipeItemScore.requiresCraftingTable(
            Collections.nCopies(
                    8,
                    new MinedResource("cobblestone", Rarity.ITS_EVERYWHERE)
            ),
            false
    );
    private static final int SIGN = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.SIGN, false
    );
    private static final int BOOKSHELF = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.BOOKSHELF, false
    );
    private static final int ENCH_TABLE = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.ENCH_TABLE, false
    );
    private static final int BREW_STAND = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.BREW_STAND, false
    );
    private static final int LANTERN = RecipeItemScore.requiresCraftingTable(
            CommonRecipes.LANTERN, false
    );
    private static final int TARGET = RecipeItemScore.requiresCraftingTable(
            ImmutableList.of(
                    new MinedResource("redstone_dust", Rarity.SOMETIMES_HARD_TO_FIND),
                    new MinedResource("redstone_dust", Rarity.SOMETIMES_HARD_TO_FIND),
                    new MinedResource("redstone_dust", Rarity.SOMETIMES_HARD_TO_FIND),
                    new MinedResource("redstone_dust", Rarity.SOMETIMES_HARD_TO_FIND),
                    new CraftedResource("hay_bale", 1, Collections.nCopies(9, new FarmedResource("wheat", Rarity.ITS_EVERYWHERE)))
            ), false
    );
    private static final int CAULDRON = RecipeItemScore.requiresCraftingTable(
            ImmutableList.copyOf(
                    Collections.nCopies(7, CraftedResources.IRON_ORE_OR_INGOT)
            ), false
    );

    // TODO: Scan all recipes on server start and check for missing weights up front
    // Currently, they only get scanned when the town flag tries to generate quests

    static {
        defaultItemWeights.add(String.format("#%s", ItemTags.BEDS.location()), BED);
        defaultItemWeights.add(String.format("#%s", ItemTags.DIRT.location()), DIRT);
        // TODO: Tags that allow for you to choose from a range of easy-to-hard blocks should use the weight of the easiest block
        // In this case, we know that "light sources" contains torches, so we use torch weight for now
        defaultItemWeights.add(String.format("#%s", TagsInit.Items.LIGHT_SOURCES.location()), TORCH_SCORE);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.TORCH).toString(), TORCH_SCORE);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.CRAFTING_TABLE).toString(), CRAFTING_TABLE);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.LANTERN).toString(), LANTERN);
        defaultItemWeights.add(String.format("#%s", TagsInit.Items.LANTERNS.location()), LANTERN);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.CHEST).toString(), CHEST);
        defaultItemWeights.add(String.format("#%s", Tags.Items.CHESTS.location()), CHEST);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.FURNACE).toString(), FURNACE);
        defaultItemWeights.add(String.format("#%s", ItemTags.SIGNS.location()), SIGN);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.BOOKSHELF).toString(), BOOKSHELF);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.ENCHANTING_TABLE).toString(), ENCH_TABLE);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.BREWING_STAND).toString(), BREW_STAND);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.TARGET).toString(), TARGET);
        defaultItemWeights.add(ForgeRegistries.ITEMS.getKey(Items.CACTUS).toString(), CAULDRON);
    }

    // TODO: How can mod pack builders add weights to this?

    public static final String FILENAME = "questown-item-weights-server.toml";

    static {
        BUILDER.push(FILENAME);

        itemWeights = BUILDER.define(ITEM_WEIGHTS, defaultItemWeights);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
