package ca.bradj.questown.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** A job's results that match the clicked item, or all of them when none match. */
public final class ProducedMatches {

    private ProducedMatches() {
    }

    public static List<ItemStack> of(List<ItemStack> results, Ingredient requested) {
        List<ItemStack> matching = results.stream().filter(requested).toList();
        if (matching.isEmpty()) {
            return results;
        }
        return matching;
    }
}
