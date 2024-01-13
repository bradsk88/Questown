package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
import net.minecraft.world.item.crafting.Ingredient;

public record WorkStates(
        int maxState,
        ImmutableMap<Integer, Ingredient> ingredientsRequired,
        ImmutableMap<Integer, Integer> ingredientQtyRequired,
        ImmutableMap<Integer, Ingredient> toolsRequired,
        ImmutableMap<Integer, Integer> workRequired,
        ImmutableMap<Integer, Integer> timeRequired
) {
}
