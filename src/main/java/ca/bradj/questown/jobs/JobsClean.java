package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

public class JobsClean {

    public static <STATUS extends IStatus<STATUS>> STATUS doOrGoTo(
            STATUS status,
            boolean isAtJobSite,
            STATUS goStatus
    ) {
        if (isAtJobSite) {
            return status;
        }
        return goStatus;
    }

    public static <HELD extends HeldItem<HELD, TOWN_ITEM>, TOWN_ITEM extends Item<TOWN_ITEM>> boolean hasNonSupplyItems(
            ItemsHolder<HELD> journal,
            ImmutableList<? extends Predicate<TOWN_ITEM>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(v.get()))));
    }

    @NotNull
    static <I> ImmutableMap<Integer, Boolean> getSupplyItemStatuses(
            Supplier<Collection<I>> journal,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Function<Integer, Boolean> anyIngredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates,
            Function<Integer, Boolean> anyToolsRequiredAtStates,
            Map<Integer, Integer> workRequiredAtStates
    ) {
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, Predicate<I>> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journal.get().stream().anyMatch(ingr);
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        for (Map.Entry<Integer, Integer> work : workRequiredAtStates.entrySet()) {
            // If work is require, but no tools or items are required: pretend we have the necessary supply items
            if (!anyIngredientsRequiredAtStates.apply(work.getKey()) && !anyToolsRequiredAtStates.apply(work.getKey())) {
                b.put(work.getKey(), true);
            }
        }
        return ImmutableMap.copyOf(b);
    }

    public static <I extends Item<I>> boolean hasNonSupplyItems(
            Collection<I> items,
            int state,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates
    ) {
        if (items.isEmpty() || items.stream().allMatch(Item::isEmpty)) {
            return false;
        }

        items = items.stream().filter(v -> !v.isEmpty()).toList();

        Predicate<I> ings = ingredientsRequiredAtStates.get(state);
        if (ings == null) {
            return items.stream().anyMatch(
                    i -> isNotToolFromAnyStage(i, toolsRequiredAtStates)
            );
        }
        return items.stream().anyMatch(v -> !ings.test(v));
    }


    @NotNull
    private static <I> boolean isNotToolFromAnyStage(
            I i,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates
    ) {
        for (Predicate<I> e : toolsRequiredAtStates.values()) {
            if (e.test(i)) {
                return false;
            }
        }
        // Item is not a tool
        return true;
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>
            > boolean shouldTakeItem(
            int invCapacity,
            Collection<? extends Predicate<I>> neededItemsIn,
            Collection<H> currentHeldItems,
            I item
    ) {
        if (neededItemsIn.isEmpty()) {
            return false;
        }

        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(Item::isEmpty)) {
            return false;
        }

        ArrayList<Predicate<I>> neededItems = new ArrayList<>(neededItemsIn);

        removeItemsAlreadyHeld(neededItems, currentHeldItems);
        return isItemNeeded(neededItems, item);
    }

    private static <I extends Item<I>> boolean isItemNeeded(Collection<Predicate<I>> itemsNeeded, I item) {
        for (Predicate<I> ingredient : itemsNeeded) {
            if (ingredient.test(item)) {
                return true;
            }
        }
        return false;
    }

    private static <I extends Item<I>, H extends HeldItem<H, I>> void removeItemsAlreadyHeld(ArrayList<Predicate<I>> ingredientsToSatisfy, Collection<H> currentHeldItems) {
        ArrayList<H> heldItemsToCheck = new ArrayList<>(currentHeldItems);
        for (int i = 0; i < ingredientsToSatisfy.size(); i++) {
            for (H heldItem : heldItemsToCheck) {
                if (ingredientsToSatisfy.get(i).test(heldItem.get())) {
                    ingredientsToSatisfy.remove(i);
                    i--;
                    heldItemsToCheck.remove(heldItem);
                    break;
                }
            }
        }
    }

    public interface SuppliesTarget<POS, TOWN_ITEM> {

        boolean isCloseTo();

        String toShortString();

        List<TOWN_ITEM> getItems();

        void removeItem(
                int i,
                int quantity
        );
    }

    public static <POS, TOWN_ITEM extends Item<TOWN_ITEM>> void tryTakeContainerItems(
            Consumer<TOWN_ITEM> villager,
            SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<TOWN_ITEM, Boolean> isRemovalCandidate
    ) {
        if (!suppliesTarget.isCloseTo()) {
            return;
        }
        String start = suppliesTarget.toShortString();
        List<TOWN_ITEM> items = suppliesTarget.getItems();
        for (int i = 0; i < items.size(); i++) {
            TOWN_ITEM mcTownItem = items.get(i);
            if (isRemovalCandidate.apply(mcTownItem)) {
                TOWN_ITEM unit = mcTownItem.unit();
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", unit.getShortName(), start);
                villager.accept(unit);
                suppliesTarget.removeItem(i, 1);
                break;
            }
        }
    }
}
