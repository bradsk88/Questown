package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;

public class CraftedResources {
    public static final CraftedResource PLANKS = new CraftedResource(
            "planks",
            4,
            ImmutableList.of(MinedResources.WOOD)
    );
    public static final CraftedResource WOOD_SLAB = new CraftedResource(
            "wood slab",
            3,
            ImmutableList.of(CraftedResources.PLANKS)
    );
    public static final Resource STICK = new CraftedResource(
            "stick",
            4,
            ImmutableList.of(CraftedResources.PLANKS, CraftedResources.PLANKS)
    );
    public static final Resource TOWN_WAND = new CraftedResource(
            "stick",
            1,
            ImmutableList.of(CraftedResources.STICK)
    );
    public static final Resource PAPER = new CraftedResource("paper", 3, ImmutableList.of(MinedResources.SUGAR_CANE));
    public static final CraftedResource BOOK = new CraftedResource(
            "book", 1,
            ImmutableList.of(
                    new MinedResource("leather", Rarity.SOMETIMES_HARD_TO_FIND),
                    CraftedResources.PAPER,
                    CraftedResources.PAPER,
                    CraftedResources.PAPER
            )
    );

    public static final MinedResource IRON_ORE_OR_INGOT = new MinedResource("iron", Rarity.SOMETIMES_HARD_TO_FIND);

    public static final MinedResource COAL = new MinedResource("coal", Rarity.EASY_TO_FIND);
    public static final CraftedResource IRON_INGOT = new CraftedResource(
            "iron_ingot",
            1,
            ImmutableList.of(IRON_ORE_OR_INGOT, COAL)
    );
    public static final CraftedResource IRON_NUGGET = new CraftedResource("iron_nug", 9, ImmutableList.of(IRON_INGOT));
    public static final Resource OBSIDIAN = new CraftedResource(
            "obsidian",
            1,
            ImmutableList.of(
                    new MinedResource("water", Rarity.ITS_EVERYWHERE),
                    new MinedResource("lava", Rarity.SOMETIMES_HARD_TO_FIND)
            )
    );
    public static final CraftedResource CLAY_BRICK = new CraftedResource(
            "clay_brick",
            1,
            ImmutableList.of(IRON_ORE_OR_INGOT, COAL)
    );


    public static final CraftedResource CAMPFIRE =  new CraftedResource(
            "campfire", 1,
            ImmutableList.of(
                    MinedResources.WOOD,
                    MinedResources.WOOD,
                    MinedResources.WOOD,
                    CraftedResources.STICK,
                    CraftedResources.STICK,
                    CraftedResources.STICK,
                    MinedResources.COAL
            )
    );


    public static final CraftedResource FLOWER_POT =  new CraftedResource(
            "flower_pot", 1,
            ImmutableList.of(
                    MinedResources.WOOD,
                    MinedResources.WOOD,
                    MinedResources.WOOD,
                    CraftedResources.STICK,
                    CraftedResources.STICK,
                    CraftedResources.STICK,
                    MinedResources.COAL
            )
    );


    public static final CraftedResource SMALL_SOUP_POT =  new CraftedResource(
            "small_soup_pot", 1,
            ImmutableList.of(
                    FLOWER_POT,
                    CAMPFIRE
            )
    );
    public static final CraftedResource STONE_PICKAXE =  new CraftedResource(
            "stone_pickaxe", 1,
            ImmutableList.of(
                    STICK,
                    STICK,
                    PLANKS,
                    PLANKS,
                    PLANKS
            )
    );
    public static final CraftedResource CRAFTING_TABLE =  new CraftedResource(
            "crafting_table", 1,
            ImmutableList.of(
                    PLANKS,
                    PLANKS,
                    PLANKS,
                    PLANKS
            )
    );
    public static final CraftedResource FURNACE =  new CraftedResource(
            "furnace", 1,
            ImmutableList.of(
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE,
                    MinedResources.COBBLESTONE
            )
    );

}
