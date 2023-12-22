package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.VillagerDataCollectionHolder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ProductionTimeWarper {

    public static <
            C extends ContainerTarget.Container<I>,
            I extends Item<I>,
            H extends HeldItem<H, I>
            >
    Collection<H> dropIntoContainers(
            ImmutableList<H> itemz,
            @NotNull ImmutableList<ContainerTarget<C, I>> containers
    ) {
        Stack<H> stack = new Stack<>();
        itemz.stream().filter(v -> !v.isEmpty() && !v.isLocked()).forEach(stack::add);
        for (ContainerTarget<C, I> container : containers) {
            if (container.isFull()) {
                continue;
            }
            for (int i = 0; i < container.size(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, stack.pop().get());
                    if (stack.isEmpty()) {
                        break;
                    }
                }
            }
        }
        if (stack.isEmpty()) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(stack);
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>,
            TOWN extends TownState<?, I, H, ?, TOWN>
            > TOWN simulateDropLoot(
            TOWN inState,
            ProductionStatus status,
            int villagerIndex,
            Supplier<H> emptyFactory
    ) {
        Collection<H> items = getHeldItems(inState, villagerIndex);
        ProductionTimeWarper.Result<H> r = new ProductionTimeWarper.Result<>(status, ImmutableList.copyOf(items));
        Function<ImmutableList<H>, Collection<H>> dropFn = itemz -> ProductionTimeWarper.dropIntoContainers(
                itemz,
                inState.containers
        );
        r = ProductionTimeWarper.simulateDropLoot(r, dropFn, emptyFactory);
        return inState.withVillagerData(villagerIndex, inState.villagers.get(villagerIndex).withItems(r.items()));
    }

    public static <H extends HeldItem<H, ?>> Collection<H> getHeldItems(
            VillagerDataCollectionHolder<H> mcTownState,
            int villagerIndex
    ) {
        TownState.VillagerData<H> vil = mcTownState.getVillager(villagerIndex);
        return vil.journal.items();
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>,
            TOWN extends TownState<?, I, H, ?, TOWN>
            >
    @Nullable TOWN simulateCollectSupplies(
            TOWN inState,
            int processingState,
            int villagerIndex,
            ImmutableMap<Integer, Predicate<H>> ingrRequiredAtStates,
            ImmutableMap<Integer, Predicate<H>> toolsRequiredAtStates,
            Function<I, H> grabber
    ) {
        Predicate<H> ingr = ingrRequiredAtStates.get(processingState);

        if (ingr == null) {
            Predicate<H> toolchk = toolsRequiredAtStates.get(processingState);
            if (toolchk == null) {
                throw new IllegalStateException("No ingredients or tools required at state " + processingState + ". We shouldn't be collecting.");
            }
            ingr = toolchk;
        }

        final Predicate<H> fingr = ingr;

        @Nullable Map.Entry<TOWN, I> removeResult = inState.withContainerItemRemoved(i -> fingr.test(grabber.apply(i)));
        if (removeResult == null) {
            return null; // Item does not exist - collection failed
        }

        TOWN outState = removeResult.getKey();

        TownState.VillagerData<H> villager = outState.villagers.get(villagerIndex);
        villager = villager.withAddedItem(grabber.apply(removeResult.getValue()));
        if (villager == null) {
            return null; // No space in inventory - collection failed
        }

        return outState.withVillagerData(villagerIndex, villager);
    }

    public record JobNeeds<I>(
            ImmutableMap<Integer, Predicate<I>> ingredients
    ) {
    }

    public record Result<H>(
            ProductionStatus status,
            ImmutableList<H> items) {
    }

    static <I extends Item<I>, H extends HeldItem<H, I>> Result<H> simulateExtractProduct(
            ProductionStatus status,
            ImmutableList<H> heldItems,
            ItemToEntityMover<I, H> taker,
            Supplier<I> remover
    ) {
        Optional<H> foundEmpty = heldItems.stream()
                .filter(Item::isEmpty)
                .findFirst();
        if (foundEmpty.isPresent()) {
            int idx = heldItems.indexOf(foundEmpty.get());
            ArrayList<H> outItems = new ArrayList<>(heldItems);
            outItems.set(idx, taker.copyFromTownWithoutRemoving(remover.get()));
            return new Result<>(
                    status,
                    ImmutableList.copyOf(outItems)
            );
        }
        throw new IllegalStateException(String.format(
                "Got extract product with full inventory: %s", heldItems
        ));

    }

    static <I extends Item<I>, H extends HeldItem<H, I> & Item<H>> Result<H> simulateDropLoot(
            Result<H> input,
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
        return new Result<>(
                input.status,
                b.build()
        );
    }

    public interface Town<I extends Item<I>, H extends HeldItem<H, I>> extends GathererStatuses.TownStateProvider {

        // Returns any items that were NOT deposited
        ImmutableList<H> depositItems(ImmutableList<H> itemsToDeposit);
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
