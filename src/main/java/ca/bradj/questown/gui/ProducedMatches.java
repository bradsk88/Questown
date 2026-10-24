package ca.bradj.questown.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * The results the "jobs that make this item" screen shows for a job: only those matching the
 * clicked item (r17, dev 2026-10-25), or all of them when none match, so the row is never blank.
 * Pinned by {@code ProducedMatchesTest}.
 */
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
