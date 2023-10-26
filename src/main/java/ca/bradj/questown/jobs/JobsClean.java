package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.*;

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
        for (int i = 0; i < invCapacity / recipe.size(); i++) {
            ingredientsToSatisfy.addAll(initial);
        }

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
}
