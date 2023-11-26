package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;

public class CommonRecipes {

    public static final ImmutableList<Resource> TORCH_INGREDIENTS = ImmutableList.of(
            MinedResources.COAL,
            CraftedResources.STICK
    );
    public static final Collection<? extends Resource> CRAFTING_TABLE = ImmutableList.of(
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS
    );
    public static final Collection<? extends Resource> SIGN = ImmutableList.of(
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.STICK
    );
    public static final Collection<? extends Resource> CHEST = ImmutableList.copyOf(
            Collections.nCopies(
                    8,
                    CraftedResources.PLANKS
            )
    );
    public static final Collection<? extends Resource> FURNACE = ImmutableList.copyOf(
            Collections.nCopies(
                    8,
                    new MinedResource("cobblestone", Rarity.ITS_EVERYWHERE)
            )
    );
    public static final Collection<? extends Resource> BOOKSHELF = ImmutableList.of(
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.BOOK,
            CraftedResources.BOOK,
            CraftedResources.BOOK,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS,
            CraftedResources.PLANKS
    );
    public static final Collection<? extends Resource> ENCH_TABLE = ImmutableList.of(
            CraftedResources.BOOK,
            new MinedResource("diamond", Rarity.RARE),
            CraftedResources.OBSIDIAN,
            new MinedResource("diamond", Rarity.RARE),
            CraftedResources.OBSIDIAN,
            CraftedResources.OBSIDIAN,

            CraftedResources.OBSIDIAN
    );
    public static final Collection<? extends Resource> BREW_STAND = ImmutableList.of(
            new CombatResource("blaze_rod", Rarity.RARE),
            new MinedResource("cobblestone", Rarity.ITS_EVERYWHERE),
            new MinedResource("cobblestone", Rarity.ITS_EVERYWHERE),
            new MinedResource("cobblestone", Rarity.ITS_EVERYWHERE)
    );

    public static final Collection<? extends Resource> LANTERN = ImmutableList.of(
            new CraftedResource("torch", 4, ImmutableList.of(
                    new MinedResource("coal", Rarity.EASY_TO_FIND),
                    CraftedResources.STICK
            )),
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET,
            CraftedResources.IRON_NUGGET
    );
}
