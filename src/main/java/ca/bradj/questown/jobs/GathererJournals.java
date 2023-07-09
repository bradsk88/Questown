package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

public class GathererJournals {

    public interface FoodRemover<I extends GathererJournal.Item> {
        // Return null if there is no food
        @Nullable I removeFood();
    }

    public interface LootGiver<I extends GathererJournal.Item> {
        // Return null if there is no food
        @NotNull Iterable<I> giveLoot();
    }

    public static <I extends GathererJournal.Item> GathererJournal.Snapshot<I> timeWarp(
            GathererJournal.Snapshot<I> input,
            long ticksPassed,
            FoodRemover<I> remover,
            LootGiver<I> lootGiver
    ) {
        GathererJournal.Snapshot<I> output = input;
        for (int i = 0; i < ticksPassed; i = getNextDaySegment(i)) {
            boolean ate = remover.removeFood() != null;

            @NotNull Iterable<I> loot = lootGiver.giveLoot();
            Iterator<I> iterator = loot.iterator();
            List<I> outItems = input.items().stream().map(
                    v -> {
                        if (v.isEmpty()) {
                            return iterator.next();
                        }
                        return v;
                    }
            ).toList();
            GathererJournal.Statuses newStatus = input.status(); // TODO: See GathererJournal.tick
            output = new GathererJournal.Snapshot<>(newStatus, ate, ImmutableList.copyOf(outItems));
        }
        return output;
    }

    public static int getNextDaySegment(int currentGameTime) {
        int timeOfDay = currentGameTime % 24000;
        if (timeOfDay < 6000) {
            return 6000;
        }
        if (timeOfDay < 11500) {
            return 11500;
        }
        if (timeOfDay < 22000) {
            return 22000;
        }
        return 24000;
    }

}
