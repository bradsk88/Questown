package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.ItemsHolder;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Supply-take logic: which container items a townie should pick up as *supplies*
 * (work inputs), as opposed to extracting a finished result (see {@code ResultGenerator}).
 *
 * <p>Migrated verbatim from {@code ca.bradj.questown.jobs.JobsClean} (2026-10-09,
 * declarative-jobs consolidation plan C4).</p>
 */
public class SupplyItems {

    public static <HELD extends HeldItem<HELD, TOWN_ITEM>, TOWN_ITEM extends Item<TOWN_ITEM>> boolean hasNonSupplyItems(
            ItemsHolder<HELD> journal,
            ImmutableList<? extends Predicate<TOWN_ITEM>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(v.get()))));
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

    private static <I extends Item<I>> boolean isItemNeeded(
            Collection<Predicate<I>> itemsNeeded,
            I item
    ) {
        for (Predicate<I> ingredient : itemsNeeded) {
            if (ingredient.test(item)) {
                return true;
            }
        }
        return false;
    }

    private static <I extends Item<I>, H extends HeldItem<H, I>> void removeItemsAlreadyHeld(
            ArrayList<Predicate<I>> ingredientsToSatisfy,
            Collection<H> currentHeldItems
    ) {
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
                int i
        );
    }

    public static <POS, TOWN_ITEM extends Item<TOWN_ITEM>> boolean tryTakeContainerItems(
            Consumer<TOWN_ITEM> villager,
            SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<TOWN_ITEM, Boolean> isRemovalCandidate,
            Function<List<TOWN_ITEM>, List<Pair<Integer, TOWN_ITEM>>> adjustOrder
    ) {
        if (!suppliesTarget.isCloseTo()) {
            return false;
        }
        String start = suppliesTarget.toShortString();
        List<Pair<Integer, TOWN_ITEM>> items = adjustOrder.apply(suppliesTarget.getItems());
        for (Pair<Integer, TOWN_ITEM> mcTownItem : items) {
            if (isRemovalCandidate.apply(mcTownItem.b())) {
                TOWN_ITEM unit = mcTownItem.b().unit();
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", unit.getShortName(), start);
                villager.accept(unit);
                suppliesTarget.removeItem(mcTownItem.a());
                return true;
            }
        }
        return false;
    }
}
