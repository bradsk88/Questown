package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class ProductionTimeWarper<I extends Item<I>, H extends HeldItem<H, I> & Item<H>, BIOME> {

    private final FoodRemover<I> remover;
    private final LootGiver<I, H, BIOME> lootGiver;
    private final Town<I, H> town;
    private final EmptyFactory<H> emptyFactory;
    private final ItemToEntityMover<I, H> converter;
    private final GathererJournal.ToolsChecker<H> toolChecker;
    private final Function<Iterable<H>, BIOME> biome;

    public ProductionTimeWarper(
            FoodRemover<I> remover,
            LootGiver<I, H, BIOME> lootGiver,
            Town<I, H> town,
            EmptyFactory<H> emptyFactory,
            ItemToEntityMover<I, H> converter,
            GathererJournal.ToolsChecker<H> toolChecker,
            Function<Iterable<H>, BIOME> biome
    ) {
        this.remover = remover;
        this.lootGiver = lootGiver;
        this.town = town;
        this.emptyFactory = emptyFactory;
        this.converter = converter;
        this.toolChecker = toolChecker;
        this.biome = biome;
    }

    public interface FoodRemover<I extends Item<I>> {
        // Return null if there is no food
        @Nullable I removeFood();
    }

    public interface LootGiver<I extends Item<I>, H extends HeldItem<H, I>, BIOME> {
        // Return null if there is no food
        @NotNull Iterable<H> giveLoot(
                int max,
                GathererJournal.Tools tools,
                BIOME biome
        );
    }

    public interface Town<I extends Item<I>, H extends HeldItem<H, I>> extends GathererStatuses.TownStateProvider {

        // Returns any items that were NOT deposited
        ImmutableList<H> depositItems(ImmutableList<H> itemsToDeposit);
    }

    public SimpleSnapshot<ProductionStatus, H> timeWarp(
            SimpleSnapshot<ProductionStatus, H> input,
            long currentTick,
            long ticksPassed,
            int lootPerDay
    ) {
        if (ticksPassed == 0) {
            return input;
        }
        SimpleSnapshot<ProductionStatus, H> output = input;
        EntityInvStateProvider<Integer> stateGetter =
                MutableEntityInvStateProvider.withInitialItems(input.items());

        long start = currentTick;
        long max = currentTick + ticksPassed;

        for (long i = start; i <= max; i = getNextDaySegment(i, max)) {
            Signals signal = Signals.fromGameTime(
                    i
            );
            GathererJournal.Status newStatus = ProductionStatuses.getNewStatusFromSignal(
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
                @NotNull Iterable<H> loot = lootGiver.giveLoot(lootPerDay, tools, biome.apply(output.items()));
                Iterator<H> iterator = loot.iterator();
                outItems = outItems.stream().map(
                        v -> {
                            if (v.isEmpty() && iterator.hasNext()) {
                                return iterator.next();
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
    public static <I extends Item<I>, H extends HeldItem<H, I>> List<H> dropLoot(
            List<H> outItems,
            Town<I, H> town,
            ItemToEntityMover<I, H> converter,
            EmptyFactory<H> emptyFactory
    ) {
        ImmutableList<H> unlockedItems = ImmutableList.copyOf(
                outItems.stream()
                        .filter(v -> !v.isLocked())
                        .toList()
        );
        Iterator<H> undeposited = town.depositItems(unlockedItems)
                .stream()
                .filter(Predicate.not(HeldItem::isEmpty))
                .iterator();
        ImmutableList.Builder<H> b = ImmutableList.builder();
        for (H item : outItems) {
            if (item.isLocked()) {
                b.add(item);
                continue;
            }
            if (undeposited.hasNext()) {
                b.add(undeposited.next());
                continue;
            }
            b.add(emptyFactory.makeEmptyItem());
        }
        outItems = b.build();
        return outItems;
    }

    public interface ItemToEntityMover<I extends Item<I>, H extends HeldItem<H, I>> {
        H takeFromTown(I item);
    }

    private static <I extends Item<I>, H extends HeldItem<H, I>> void takeButDoNotEatFood(
            List<H> outItems,
            I food,
            Signals signal,
            ItemToEntityMover<I, H> mover
    ) {
        // TODO: More efficient way to do this?
        Optional<H> foundEmpty = outItems.stream()
                .filter(Item::isEmpty)
                .findFirst();
        if (foundEmpty.isPresent()) {
            // TODO: This is suspiciously similar to the logic in VisitorMobJob
            int idx = outItems.indexOf(foundEmpty.get());
            outItems.set(idx, mover.takeFromTown(food));
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
