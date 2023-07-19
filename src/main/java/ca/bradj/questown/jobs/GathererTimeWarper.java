package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class GathererTimeWarper<I extends GathererJournal.Item<I>> {

    private final FoodRemover<I> remover;
    private final LootGiver<I> lootGiver;
    private final Town<I> town;
    private final GathererJournal.EmptyFactory<I> emptyFactory;

    public GathererTimeWarper(
            FoodRemover<I> remover,
            LootGiver<I> lootGiver,
            Town<I> town,
            GathererJournal.EmptyFactory<I> emptyFactory
    ) {
        this.remover = remover;
        this.lootGiver = lootGiver;
        this.town = town;
        this.emptyFactory = emptyFactory;
    }

    public interface FoodRemover<I extends GathererJournal.Item<I>> {
        // Return null if there is no food
        @Nullable I removeFood();
    }

    public interface LootGiver<I extends GathererJournal.Item<I>> {
        // Return null if there is no food
        @NotNull Iterable<I> giveLoot(int max);
    }

    public interface Town<I extends GathererJournal.Item<I>> extends Statuses.TownStateProvider {

        // Returns any items that were NOT deposited
        ImmutableList<I> depositItems(ImmutableList<I> itemsToDeposit);
    }

    public GathererJournal.Snapshot<I> timeWarp(
            GathererJournal.Snapshot<I> input,
            long currentTick,
            long ticksPassed,
            int lootPerDay
    ) {
        if (ticksPassed == 0) {
            return input;
        }
        GathererJournal.Snapshot<I> output = input;
        MutableInventoryStateProvider<I> stateGetter =
                MutableInventoryStateProvider.withInitialItems(input.items());

        long start = currentTick;
        long max = currentTick + ticksPassed;

        for (long i = start; i <= max; i = getNextDaySegment(i, max)) {
            GathererJournal.Signals signal = GathererJournal.Signals.fromGameTime(
                    i
            );
            GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                    output.status(), signal, stateGetter, town
            );
            if (newStatus == null) {
                continue;
            }
            List<I> outItems = new ArrayList<>(output.items());
            if (newStatus == GathererJournal.Status.NO_FOOD) {
                I food = remover.removeFood();
                if (food != null) {
                    takeButDoNotEatFood(outItems, food, signal);
                }
            }
            if (newStatus == GathererJournal.Status.GATHERING_EATING) {
                output = output.withStatus(newStatus)
                        .eatFoodFromInventory(emptyFactory, signal);
                outItems = output.items();
                newStatus = output.status();
            }
            if (newStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                @NotNull Iterable<I> loot = lootGiver.giveLoot(lootPerDay);
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
                Iterator<I> undeposited = town.depositItems(itemsToDeposit)
                        .stream()
                        .filter(Predicate.not(GathererJournal.Item::isEmpty))
                        .iterator();
                outItems = outItems.stream().map(
                        v -> {
                            if (undeposited.hasNext()) {
                                return undeposited.next();
                            }
                            return emptyFactory.makeEmptyItem();
                        }
                ).toList();
            }
            ImmutableList<I> outImItems = ImmutableList.copyOf(outItems);
            stateGetter.updateItems(outImItems);
            output = new GathererJournal.Snapshot<>(newStatus, outImItems);
        }
        return output;
    }

    private static <I extends GathererJournal.Item<I>> void takeButDoNotEatFood(
            List<I> outItems,
            I food,
            GathererJournal.Signals signal
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
            throw new IllegalStateException(String.format("Got NO_FOOD with full inventory on signal %s: %s", signal, outItems));
        }
    }

    public static long getNextDaySegment(
            long currentGameTime,
            long upTo
    ) {
        if (currentGameTime == upTo) {
            return currentGameTime + 1;
        }
        long daysPassed = currentGameTime / 24000;
        long i = (24000 * daysPassed) + getNextSingleDatSegment(currentGameTime);
        return i;
    }

    private static long getNextSingleDatSegment(long currentGameTime) {
        long timeOfDay = currentGameTime % 24000;
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
