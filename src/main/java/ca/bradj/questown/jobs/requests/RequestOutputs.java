package ca.bradj.questown.jobs.requests;

import ca.bradj.questown.gui.Ingredients;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** What a job's {@code initial_request} adds to the job board's products. A tag stays one tag, shown as its category. */
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
