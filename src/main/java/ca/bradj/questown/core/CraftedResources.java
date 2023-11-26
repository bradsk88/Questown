package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;

public class CraftedResources {
    public static final CraftedResource PLANKS = new CraftedResource("planks",
            4,
            ImmutableList.of(MinedResources.WOOD)
    );
    public static final Resource STICK = new CraftedResource("stick",
            4,
            ImmutableList.of(CraftedResources.PLANKS, CraftedResources.PLANKS)
    );
    public static final Resource PAPER = new CraftedResource("paper", 3, ImmutableList.of(MinedResources.SUGAR_CANE));
    public static final CraftedResource BOOK = new CraftedResource("book",
            1,
            ImmutableList.of(new MinedResource("leather", Rarity.SOMETIMES_HARD_TO_FIND),
                    CraftedResources.PAPER,
                    CraftedResources.PAPER,
                    CraftedResources.PAPER
            )
    );

    public static final MinedResource IRON_ORE_OR_INGOT = new MinedResource(
            "iron", Rarity.SOMETIMES_HARD_TO_FIND
    );

    public static final CraftedResource IRON_NUGGET = new CraftedResource("iron_nug",
            9,
            ImmutableList.of(new CraftedResource("iron_ingot",
                    3,
                    ImmutableList.of(IRON_ORE_OR_INGOT, new MinedResource("coal", Rarity.EASY_TO_FIND))
            ))
    );
    public static final Resource OBSIDIAN = new CraftedResource(
            "obsidian",
            1,
            ImmutableList.of(new MinedResource("water", Rarity.ITS_EVERYWHERE), new MinedResource("lava", Rarity.SOMETIMES_HARD_TO_FIND))
    );
}
