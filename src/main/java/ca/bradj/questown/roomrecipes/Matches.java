package ca.bradj.questown.roomrecipes;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.quests.QuestBatchSeed;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Matches {
    public static void runForTopMatch(
            Supplier<Map<ResourceLocation, RoomRecipe>> l,
            RoomRecipeMatch<MCRoom> match,
            Consumer<ResourceLocation> rlFunc
    ) {
        Optional<ResourceLocation> topMatch = getTopMatch(l, match);
        topMatch.ifPresentOrElse(rlFunc, () -> QT.FLAG_LOGGER.error("Room match had no recipe IDs"));
    }

    public static @NotNull Optional<ResourceLocation> getTopMatch(
            Supplier<Map<ResourceLocation, RoomRecipe>> l,
            RoomRecipeMatch<MCRoom> match
    ) {
        if (match == null) {
            return Optional.empty();
        }
        return match.getRecipeIDs()
                    .stream()
                    .max(Comparator.comparingInt(v -> getCost(l, v)));
    }

    private static int getCost(
            Supplier<Map<ResourceLocation, RoomRecipe>> l,
            ResourceLocation r
    ) {
        return QuestBatchSeed.computeQuestCost(l, r, Integer.MAX_VALUE);
    }

    public static String toString(RoomRecipeMatch<MCRoom> value) {
        return String.format(
                "[%s]",
                String.join(", ", value.getRecipeIDs().stream().map(ResourceLocation::getPath).toList())
        );
    }
}
