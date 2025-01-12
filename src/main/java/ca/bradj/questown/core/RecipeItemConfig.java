package ca.bradj.questown.core;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.SmallSoupPotBlock;
import ca.bradj.questown.blocks.SoupPotBlock;
import ca.bradj.questown.core.init.TagsInit;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

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
    private static final int STICK_SCORE = RecipeItemScore.canCraftInFourGrid(
            CommonRecipes.STICK_INGREDIENTS, false
    );

    private static final int CRAFTING_TABLE = RecipeItemScore.canCraftInFourGrid(
            CommonRecipes.CRAFTING_TABLE, false
    );
    private static final int BED = RecipeItemScore.requiresCraftingTable(
            ImmutableList.of(
                    new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
                    new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
                    new MinedResource("wool", Rarity.SOMETIMES_HARD_TO_FIND),
                    new CraftedResource(
                            "planks", 4, ImmutableList.of(
                            new MinedResource("wood", Rarity.EASY_TO_FIND))
                    ),
                    new CraftedResource(
                            "planks", 4, ImmutableList.of(
                            new MinedResource("wood", Rarity.EASY_TO_FIND))
                    ),
                    new CraftedResource(
                            "planks", 4, ImmutableList.of(
                            new MinedResource("wood", Rarity.EASY_TO_FIND))
                    )
            ), true
    );
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
                    new CraftedResource(
                            "hay_bale",
                            1,
                            Collections.nCopies(9, new FarmedResource("wheat", Rarity.ITS_EVERYWHERE))
                    )
            ), false
    );
    private static final int CAULDRON = RecipeItemScore.requiresCraftingTable(
            ImmutableList.copyOf(
                    Collections.nCopies(7, CraftedResources.IRON_ORE_OR_INGOT)
            ), false
    );

    private static final int HOSPITAL_BED = STICK_SCORE + BED;

    // TODO: Scan all recipes on server start and check for missing weights up front
    //  Currently, they only get scanned when the town flag tries to generate quests

    public static final int SMALL_SOUP_POT = RecipeItemScore.canCraftInFourGrid(
            ImmutableList.of(
                    CraftedResources.FLOWER_POT,
                    CraftedResources.CAMPFIRE
            ), false
    );

    public static final int BIG_SOUP_POT = RecipeItemScore.requiresCraftingTable(
            ImmutableList.of(
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.SMALL_SOUP_POT,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK,
                    CraftedResources.CLAY_BRICK
            ), false
    );

    static {
        add(String.format("#%s", ItemTags.BEDS.location()), BED);
        add(String.format("#%s", ItemTags.DIRT.location()), DIRT);
        // TODO: Tags that allow for you to choose from a range of easy-to-hard blocks should use the weight of the easiest block
        // In this case, we know that "light sources" contains torches, so we use torch weight for now
        add(String.format("#%s", TagsInit.Items.LIGHT_SOURCES.location()), TORCH_SCORE);
        add(assumePresent(Items.TORCH), TORCH_SCORE);
        add(assumePresent(Items.CRAFTING_TABLE), CRAFTING_TABLE);
        add(assumePresent(Items.LANTERN), LANTERN);
        add(String.format("#%s", TagsInit.Items.LANTERNS.location()), LANTERN);
        add(assumePresent(Items.CHEST), CHEST);
        add(String.format("#%s", Tags.Items.CHESTS.location()), CHEST);
        add(assumePresent(Items.FURNACE), FURNACE);
        add(String.format("#%s", ItemTags.SIGNS.location()), SIGN);
        add(assumePresent(Items.BOOKSHELF), BOOKSHELF);
        add(assumePresent(Items.ENCHANTING_TABLE), ENCH_TABLE);
        add(assumePresent(Items.BREWING_STAND), BREW_STAND);
        add(assumePresent(Items.TARGET), TARGET);
        add(assumePresent(Items.CAULDRON), CAULDRON);
        add(Questown.ResourceLocation(SmallSoupPotBlock.ITEM_ID), SMALL_SOUP_POT);
        add(SoupPotBlock.ITEM_ID, BIG_SOUP_POT);
//        add(ForgeRegistries.ITEMS.getKey(ItemsInit.HOSPITAL_BED.get()).toString(), HOSPITAL_BED);
    }

    @SuppressWarnings("DataFlowIssue")
    private static @NotNull ResourceLocation assumePresent(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }

    private static void add(
            Object itemOrTagKey,
            int cost
    ) {
        defaultItemWeights.add(itemOrTagKey.toString(), cost);
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
