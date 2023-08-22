package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class GathererTimeWarper<I extends GathererJournal.Item<I>, H extends HeldItem<H, I> & GathererJournal.Item<H>> {

    private final FoodRemover<I> remover;
    private final LootGiver<I> lootGiver;
    private final Town<I> town;
    private final GathererJournal.EmptyFactory<H> emptyFactory;
    private final ItemToEntityMover<I, H> converter;
    private final GathererJournal.ToolsChecker<H> toolChecker;

    public GathererTimeWarper(
            FoodRemover<I> remover,
            LootGiver<I> lootGiver,
            Town<I> town,
            GathererJournal.EmptyFactory<H> emptyFactory,
            ItemToEntityMover<I, H> converter,
            GathererJournal.ToolsChecker<H> toolChecker
    ) {
        this.remover = remover;
        this.lootGiver = lootGiver;
        this.town = town;
        this.emptyFactory = emptyFactory;
        this.converter = converter;
        this.toolChecker = toolChecker;
    }

    public interface FoodRemover<I extends GathererJournal.Item<I>> {
        // Return null if there is no food
        @Nullable I removeFood();
    }

    public interface LootGiver<I extends GathererJournal.Item<I>> {
        // Return null if there is no food
        @NotNull Iterable<I> giveLoot(int max, GathererJournal.Tools tools);
    }

    public interface Town<I extends GathererJournal.Item<I>> extends Statuses.TownStateProvider {

        // Returns any items that were NOT deposited
        ImmutableList<I> depositItems(ImmutableList<I> itemsToDeposit);
    }

    public GathererJournal.Snapshot<H> timeWarp(
            GathererJournal.Snapshot<H> input,
            long currentTick,
            long ticksPassed,
            int lootPerDay
    ) {
        if (ticksPassed == 0) {
            return input;
        }
        GathererJournal.Snapshot<H> output = input;
        MutableInventoryStateProvider<H> stateGetter =
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
            List<H> outItems = new ArrayList<>(output.items());
            if (newStatus == GathererJournal.Status.NO_FOOD) {
                I food = remover.removeFood();
                if (food != null) {
                    takeButDoNotEatFood(outItems, food, signal, this.converter);
                }
            }
            if (newStatus == GathererJournal.Status.GATHERING_EATING) {
                output = output.withStatus(newStatus)
                        .eatFoodFromInventory(emptyFactory, signal);
                outItems = output.items();
                newStatus = output.status();
            }
            if (newStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                GathererJournal.Tools tools = this.toolChecker.computeTools(output.items());
                @NotNull Iterable<I> loot = lootGiver.giveLoot(lootPerDay, tools);
                Iterator<I> iterator = loot.iterator();
                outItems = outItems.stream().map(
                        v -> {
                            if (v.isEmpty()) {
                                return converter.convert(iterator.next());
                            }
                            return v;
                        }
                ).toList();
            }
            if (newStatus == GathererJournal.Status.DROPPING_LOOT) {
                outItems = dropLoot(outItems, town, converter, emptyFactory);
            }
            ImmutableList<H> outImItems = ImmutableList.copyOf(outItems);
            stateGetter.updateItems(outImItems);
            output = new GathererJournal.Snapshot<>(newStatus, outImItems);
        }
        return output;
    }

    @NotNull
    public static <I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> List<H> dropLoot(
            List<H> outItems,
            Town<I> town,
            ItemToEntityMover<I, H> converter,
            GathererJournal.EmptyFactory<H> emptyFactory
    ) {
        ImmutableList<I> itemsToDeposit = ImmutableList.copyOf(
                outItems.stream()
                        .filter(v -> !v.isLocked())
                        .map(HeldItem::get)
                        .toList()
        );
        Iterator<I> undeposited = town.depositItems(itemsToDeposit)
                .stream()
                .filter(Predicate.not(GathererJournal.Item::isEmpty))
                .iterator();
        ImmutableList.Builder<H> b = ImmutableList.builder();
        for (H item : outItems) {
            if (item.isLocked()) {
                b.add(item);
                continue;
            }
            if (undeposited.hasNext()) {
                b.add(converter.convert(undeposited.next()));
                continue;
            }
            b.add(emptyFactory.makeEmptyItem());
        }
        outItems = b.build();
        return outItems;
    }

    public interface ItemToEntityMover<I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> {
        H convert(I item);
    }

    private static <I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> void takeButDoNotEatFood(
            List<H> outItems,
            I food,
            GathererJournal.Signals signal,
            ItemToEntityMover<I, H> mover
    ) {
        // TODO: More efficient way to do this?
        Optional<H> foundEmpty = outItems.stream()
                .filter(GathererJournal.Item::isEmpty)
                .findFirst();
        if (foundEmpty.isPresent()) {
            // TODO: This is suspiciously similar to the logic in VisitorMobJob
            int idx = outItems.indexOf(foundEmpty.get());
            outItems.set(idx, mover.convert(food));
        } else {
            throw new IllegalStateException(String.format(
                    "Got NO_FOOD with full inventory on signal %s: %s",
                    signal,
                    outItems
            ));
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
