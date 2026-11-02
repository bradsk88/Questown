package ca.bradj.questown.jobs.requests;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Categories a player can request on the job board ("Food", "Swords") instead of one item. A category is offered while some unlocked job makes something in it. */
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

    /** The products without those that are a whole offered category; the category's own tile covers them. */
    public static <I> List<I> withoutCategories(
            Collection<I> products,
            Function<I, @Nullable ResourceLocation> tagOf,
            Collection<ResourceLocation> offered
    ) {
        return products.stream()
                .filter(p -> !isOffered(tagOf.apply(p), offered))
                .toList();
    }

    private static boolean isOffered(@Nullable ResourceLocation tag, Collection<ResourceLocation> offered) {
        if (tag == null) {
            return false;
        }
        return offered.contains(tag);
    }
}
