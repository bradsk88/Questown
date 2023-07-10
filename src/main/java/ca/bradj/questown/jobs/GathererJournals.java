package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class GathererJournals {

    public interface FoodRemover<I extends GathererJournal.Item> {
        // Return null if there is no food
        @Nullable I removeFood();
    }

    public interface LootGiver<I extends GathererJournal.Item> {
        // Return null if there is no food
        @NotNull Iterable<I> giveLoot();
    }

    public interface Town<I extends GathererJournal.Item> extends Statuses.TownStateProvider {

        // Returns any items that were NOT deposited
        ImmutableList<I> depositItems(ImmutableList<I> itemsToDeposit);
    }

    public static <I extends GathererJournal.Item> GathererJournal.Snapshot<I> timeWarp(
            GathererJournal.Snapshot<I> input,
            long ticksPassed,
            // TODO: Make TimeWarper class and use the params below as fields
            FoodRemover<I> remover,
            LootGiver<I> lootGiver,
            Town<I> town,
            GathererJournal.EmptyFactory<I> emptyFactory
    ) {
        GathererJournal.Snapshot<I> output = input;
        MutableInventoryStateProvider<I> stateGetter =
                MutableInventoryStateProvider.withInitialItems(input.items());

        // FIXME: timeWarp should take "last game time" on/with the snapshot,
        //  otherwise this is relative to time 0, which is not correct.
        for (int i = 0; i <= ticksPassed; i = getNextDaySegment(i, ticksPassed)) {
            GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                    output.status(), GathererJournal.Signals.fromGameTime(
                            i
                    ), stateGetter, town
            );
            if (newStatus == null) {
                continue;
            }
            List<I> outItems = new ArrayList<>(output.items());
            if (newStatus == GathererJournal.Status.NO_FOOD) {
                I food = remover.removeFood();
                if (food != null) {
                    takeButDoNotEatFood(outItems, food);
                }
            }
            if (newStatus == GathererJournal.Status.GATHERING_EATING) {
                output = output.withStatus(newStatus).eatFoodFromInventory(emptyFactory, GathererJournal.Signals.fromGameTime(i));
                outItems = output.items();
                newStatus = output.status();
            }
            if (newStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                @NotNull Iterable<I> loot = lootGiver.giveLoot();
                Iterator<I> iterator = loot.iterator();
                outItems = outItems.stream().map(
                        v -> {
                            if (v.isEmpty()) {
                                return iterator.next();
                            }
                            return v;
                        }
                ).toList();
            }
            if (newStatus == GathererJournal.Status.DROPPING_LOOT) {
                ImmutableList<I> itemsToDeposit = ImmutableList.copyOf(outItems);
                outItems = town.depositItems(itemsToDeposit);
            }
            ImmutableList<I> outImItems = ImmutableList.copyOf(outItems);
            stateGetter.updateItems(outImItems);
            output = new GathererJournal.Snapshot<>(newStatus, outImItems);
        }
        return output;
    }

    private static <I extends GathererJournal.Item> void takeButDoNotEatFood(
            List<I> outItems,
            I food
    ) {
        // TODO: More efficient way to do this?
        Optional<I> foundEmpty = outItems.stream()
                .filter(GathererJournal.Item::isEmpty)
                .findFirst();
        if (foundEmpty.isPresent()) {
            // TODO: This is suspiciously similar to the logic in VisitorMobJob
            int idx = outItems.indexOf(foundEmpty.get());
            outItems.set(idx, food);
        } else {
            throw new IllegalStateException("Got NO_FOOD with full inventory.");
        }
    }

    public static int getNextDaySegment(int currentGameTime, long upTo) {
        if (currentGameTime == upTo) {
            return currentGameTime + 1;
        }
        int timeOfDay = currentGameTime % 24000;
        // Allow ten ticks per period for multi-step status transitions
        if (timeOfDay < 10) {
            return timeOfDay + 1;
        }
        if (timeOfDay < 6000) {
            return 6000;
        }
        if (timeOfDay < 6010) {
            return timeOfDay + 1;
        }
        if (timeOfDay < 11500) {
            return 11500;
        }
        if (timeOfDay < 11510) {
            return timeOfDay + 1;
        }
        if (timeOfDay < 22000) {
            return 22000;
        }
        if (timeOfDay < 22010) {
            return timeOfDay + 1;
        }
        return 24000;
    }

}
