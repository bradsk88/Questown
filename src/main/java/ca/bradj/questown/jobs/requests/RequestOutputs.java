package ca.bradj.questown.jobs.requests;

import ca.bradj.questown.gui.Ingredients;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * What a job's {@code initial_request} adds to the job board's products. A tag request stays one
 * tag ingredient: the picker draws it as its category's cycling tile ("Food"). Splitting it into
 * its members offered every food in {@code #questown:villager_food}, carrot and apple included,
 * though no job makes them (r16 #18, dev 2026-10-24). Pinned by {@code RequestOutputsTest}.
 */
public final class RequestOutputs {

    private RequestOutputs() {
    }

    public static List<Ingredient> of(@Nullable Ingredient request) {
        if (request == null) {
            return List.of();
        }
        if (Ingredients.getTag(request) != null) {
            return List.of(request);
        }
        return Arrays.stream(request.getItems()).map(Ingredient::of).toList();
    }
}
