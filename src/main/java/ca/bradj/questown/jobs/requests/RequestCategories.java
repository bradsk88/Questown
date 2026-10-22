package ca.bradj.questown.jobs.requests;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Categories a player can request on the job board ("Food", "Swords") instead of one exact item.
 * A cook starts with a few recipes, but the player just wants food (dev, 2026-10-22). A category is
 * an item tag; the request is an ordinary tag {@code WorkRequest}, so any job whose products fall in
 * the tag answers it. A category is offered only while some unlocked job can make something in it.
 */
public final class RequestCategories {

    public static final List<ResourceLocation> CATEGORIES = List.of(
            new ResourceLocation("questown", "villager_food"),
            new ResourceLocation("questown", "villager_raw_food"),
            new ResourceLocation("questown", "raw_fishes"),
            new ResourceLocation("questown", "swords"),
            new ResourceLocation("questown", "axes"),
            new ResourceLocation("questown", "pickaxes"),
            new ResourceLocation("questown", "shovels"),
            new ResourceLocation("questown", "hoes"),
            new ResourceLocation("questown", "fishing_rods")
    );

    private RequestCategories() {
    }

    /** The categories, in list order, that at least one producible item belongs to. */
    public static <I> List<ResourceLocation> offered(
            Collection<I> producible,
            BiPredicate<I, ResourceLocation> inCategory
    ) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation c : CATEGORIES) {
            if (producible.stream().anyMatch(i -> inCategory.test(i, c))) {
                out.add(c);
            }
        }
        return out;
    }
}
