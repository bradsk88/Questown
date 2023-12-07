package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public interface TestFn<I extends Item<I>> {
        boolean test(I item);
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>
            > boolean shouldTakeItem(
            int invCapacity,
            Collection<TestFn<I>> recipe,
            Collection<H> currentHeldItems,
            I item
    ) {
        if (recipe.isEmpty()) {
            return false;
        }

        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(Item::isEmpty)) {
            return false;
        }

        ArrayList<H> heldItemsToCheck = new ArrayList<>(currentHeldItems);

        ImmutableList<TestFn<I>> initial = ImmutableList.copyOf(recipe);
        ArrayList<TestFn<I>> ingredientsToSatisfy = new ArrayList<>();
        ingredientsToSatisfy.addAll(initial);

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
        for (TestFn<I> ingredient : ingredientsToSatisfy) {
            if (ingredient.test(item)) {
                return true;
            }
        }
        return false;
    }

    public interface SuppliesTarget<POS, TOWN_ITEM> {

        boolean isCloseTo();

        String toShortString();

        List<TOWN_ITEM> getItems();

        void removeItem(int i, int quantity);
    }

    public static <POS, TOWN_ITEM> void tryTakeContainerItems(
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
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", mcTownItem, start);
                villager.accept(mcTownItem);
                suppliesTarget.removeItem(i, 1);
                break;
            }
        }
    }
}
