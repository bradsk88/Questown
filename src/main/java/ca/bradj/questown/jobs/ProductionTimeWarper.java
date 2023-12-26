package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.TownFlagState;
import ca.bradj.questown.town.interfaces.MutableWorkStatusHandle;
import ca.bradj.questown.town.interfaces.TimerHandle;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ProductionTimeWarper<I extends Item<I>, H extends HeldItem<H, I> & Item<H>, BIOME, TICK_SOURCE> {

    private final Function<ProductionStatus, @Nullable I> itemRemover;
    private final LootGiver<I, H, BIOME> lootGiver;
    private final Town<I, H> town;
    private final EmptyFactory<H> emptyFactory;
    private final ItemToEntityMover<I, H> taker;
    private final GathererJournal.ToolsChecker<H> toolChecker;
    private final Function<Iterable<H>, BIOME> biome;

    private final ImmutableMap<ProductionStatus, Function<WarpResult<H>, WarpResult<H>>> handlers;
    private final TimerHandle<?, TICK_SOURCE> workStatus;

    public record JobNeeds<I>(
            ImmutableMap<Integer, Predicate<I>> ingredients
    ) {}

    public ProductionTimeWarper(
            Function<ProductionStatus, @Nullable I> itemRemover,
            LootGiver<I, H, BIOME> lootGiver,
            Town<I, H> town,
            TimerHandle<?, TICK_SOURCE> workStatus,
            EmptyFactory<H> emptyFactory,
            ItemToEntityMover<I, H> taker,
            GathererJournal.ToolsChecker<H> toolChecker,
            Function<Iterable<H>, BIOME> heldBiome
    ) {
        this.itemRemover = itemRemover;
        this.lootGiver = lootGiver;
        this.town = town;
        this.emptyFactory = emptyFactory;
        this.taker = taker;
        this.toolChecker = toolChecker;
        this.biome = heldBiome;
        this.workStatus = workStatus;

        ImmutableMap.Builder<ProductionStatus, Function<WarpResult<H>, WarpResult<H>>> b = ImmutableMap.builder();
        b.put(ProductionStatus.EXTRACTING_PRODUCT, r -> ProductionTimeWarper.simulateExtractProduct(
                r.status, r.items, taker, () -> itemRemover.apply(ProductionStatus.EXTRACTING_PRODUCT)
        ));
        b.put(ProductionStatus.DROPPING_LOOT, r -> ProductionTimeWarper.simulateDropLoot(
                r, town::depositItems, emptyFactory::makeEmptyItem
        ));
        b.put(ProductionStatus.COLLECTING_SUPPLIES, r -> ProductionTimeWarper.simulateExtractProduct(
                r.status, r.items, taker, () -> itemRemover.apply(ProductionStatus.COLLECTING_SUPPLIES)
        ));
        b.put(ProductionStatus.RELAXING, r -> r);
        b.put(ProductionStatus.WAITING_FOR_TIMED_STATE, r -> r);
        b.put(ProductionStatus.NO_SPACE, r -> r);
        b.put(ProductionStatus.GOING_TO_JOB, r -> r);
        b.put(ProductionStatus.NO_SUPPLIES, r -> r);
        b.put(ProductionStatus.IDLE, r -> r);
        // TODO: Handle the remaining statuses

        this.handlers = b.build();
        assertAllStatusesHandled();
    }

    private void assertAllStatusesHandled() {
        for (ProductionStatus v : ProductionStatus.allStatuses()) {
            if (handlers.containsKey(v)) {
                continue;
            }
            throw new ExceptionInInitializerError("Not all statuses are handled (failed on " + v + ")");
        }
        ;
    }

    static <I extends Item<I>, H extends HeldItem<H, I>> WarpResult<H> simulateExtractProduct(
            ProductionStatus status, ImmutableList<H> items, ItemToEntityMover<I, H> taker, Supplier<I> remover
    ) {
        // TODO: More efficient way to do this?
        Optional<H> foundEmpty = items.stream()
                .filter(Item::isEmpty)
                .findFirst();
        if (foundEmpty.isPresent()) {
            // TODO: This is suspiciously similar to the logic in VisitorMobJob
            int idx = items.indexOf(foundEmpty.get());
            ArrayList<H> outItems = new ArrayList<>(items);
            outItems.set(idx, taker.copyFromTownWithoutRemoving(remover.get()));
            // TODO[ASAP]: Update state of block?
            return new WarpResult<>(
                    status,
                    ImmutableList.copyOf(outItems)
            );
        }
        throw new IllegalStateException(String.format(
                "Got extract product with full inventory: %s", items
        ));

    }

    static <I extends Item<I>, H extends HeldItem<H, I> & Item<H>> WarpResult<H> simulateDropLoot(
            WarpResult<H> input,
            Function<ImmutableList<H>, ? extends Collection<H>> addToTownOrReturn,
            Supplier<H> emptyFactory
    ) {
        if (input.items.isEmpty() || input.items.stream().allMatch(Item::isEmpty)) {
            throw new IllegalStateException("Got DROP_LOOT with empty inventory");
        }

        ImmutableList<H> unlockedItems = ImmutableList.copyOf(
                input.items.stream()
                        .filter(v -> !v.isLocked())
                        .toList()
        );
        Iterator<H> undeposited = addToTownOrReturn.apply(unlockedItems)
                .stream()
                .filter(Predicate.not(HeldItem::isEmpty))
                .iterator();
        ImmutableList.Builder<H> b = ImmutableList.builder();
        for (H item : input.items) {
            if (item.isLocked()) {
                b.add(item);
                continue;
            }
            if (undeposited.hasNext()) {
                b.add(undeposited.next());
                continue;
            }
            b.add(emptyFactory.get());
        }
        return new WarpResult<>(
                input.status,
                b.build()
        );
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

    public record WarpResult<H>(
            ProductionStatus status,
            ImmutableList<H> items
    ) {
    }

    public SimpleSnapshot<ProductionStatus, H> timeWarp(
            Function<WarpResult<H>, WarpResult<H>> tryWorking,
            SimpleSnapshot<ProductionStatus, H> input,
            long currentTick,
            long ticksPassed,
            int lootPerDay
    ) {
        if (ticksPassed == 0) {
            return input;
        }
        SimpleSnapshot<ProductionStatus, H> output = input;
        MutableEntityInvStateProvider<H> stateHandle =
                MutableEntityInvStateProvider.withInitialItems(input.items());

        long start = currentTick;
        long max = currentTick + ticksPassed;

        long prevI = start;
        for (long i = start; i <= max; i = getNextDaySegment(i, max)) {
            long passed = i - prevI;
            prevI = i;
            Signals signal = Signals.fromGameTime(i);
            ProductionStatus newStatus = null; // TODO[ASAP]: Uncomment
//            ProductionStatus newStatus = ProductionStatuses.getNewStatusFromSignal(
//                    output.status(), signal, stateHandle, town
//            );
//            if (newStatus == null) {
//                continue;
//            }
            WarpResult<H> r;
            if (newStatus.isWorkingOnProduction()) {
                r = tryWorking.apply(new WarpResult<>(newStatus, input.items()));
            } else {
                r = this.simulateEffects(newStatus, input.items());
            }
            ImmutableList<H> outImItems = ImmutableList.copyOf(r.items);
            stateHandle.updateItems(outImItems);
            output = new SimpleSnapshot<>(input.jobId(), r.status, outImItems);
        }
        return output;
    }

    private WarpResult<H> simulateEffects(ProductionStatus inputStatus, ImmutableList<H> items) {
        return this.handlers.get(inputStatus).apply(
                new WarpResult<>(inputStatus, items)
        );
//        if (newStatus == GathererJournal.Status.NO_FOOD) {
//            I food = remover.removeFood();
//            if (food != null) {
//                takeButDoNotEatFood(outItems, food, signal, this.converter);
//            }
//        }
//        if (newStatus == GathererJournal.Status.GATHERING_EATING) {
//            output = output.withStatus(newStatus)
//                    .eatFoodFromInventory(emptyFactory, signal);
//            outItems = output.items();
//            newStatus = output.status();
//        }
//        if (newStatus == GathererJournal.Status.RETURNED_SUCCESS) {
//            GathererJournal.Tools tools = this.toolChecker.computeTools(output.items());
//            @NotNull Iterable<H> loot = lootGiver.giveLoot(lootPerDay, tools, biome.apply(output.items()));
//            Iterator<H> iterator = loot.iterator();
//            outItems = outItems.stream().map(
//                    v -> {
//                        if (v.isEmpty() && iterator.hasNext()) {
//                            return iterator.next();
//                        }
//                        return v;
//                    }
//            ).toList();
//        }
//        if (newStatus == GathererJournal.Status.DROPPING_LOOT) {
//            outItems = dropLoot(outItems, town, converter, emptyFactory);
//        }
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
        H copyFromTownWithoutRemoving(I item);
    }

    public static long getNextDaySegment(
            long currentGameTime,
            long upTo
    ) {
        if (currentGameTime == upTo) {
            return currentGameTime + 1;
        }
        long daysPassed = currentGameTime / 24000;
        long i = (24000 * daysPassed) + getNextSingleDaySegment(currentGameTime);
        return i;
    }

    private static long getNextSingleDaySegment(long currentGameTime) {
        // TODO: Increment by 100 or some other larger number to simulate
        //  the need for villager movement through town.
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
