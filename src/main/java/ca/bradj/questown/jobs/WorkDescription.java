package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @param productConsumed true when the job's finished product is used up on extraction (a diner's
 *                        meal, a rest) rather than handed to the villager for storage. Full storage
 *                        never stops such a job (see {@code JobStatuses.Job#finishedProductNeedsStorage}).
 */
public record WorkDescription(
        Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> currentlyPossibleResults,
        Function<ServerLevel, @Nullable Ingredient> initialRequest,
        boolean productConsumed
) {
    public WorkDescription(
            Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> currentlyPossibleResults,
            Function<ServerLevel, @Nullable Ingredient> initialRequest
    ) {
        this(currentlyPossibleResults, initialRequest, false);
    }
}
