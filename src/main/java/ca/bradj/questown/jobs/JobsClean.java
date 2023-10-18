package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.*;

public class JobsClean {

    public static GathererJournal.Status doOrGoTo(
            GathererJournal.Status status,
            boolean isAtJobSite
    ) {
        if (isAtJobSite) {
            return status;
        }
        return GathererJournal.Status.GOING_TO_JOBSITE;
    }

    public interface TestFn<I extends GathererJournal.Item<I>> {
        boolean test(I item);
    }

    public static <
            I extends GathererJournal.Item<I>,
            H extends HeldItem<H, I>
            > boolean shouldTakeItem(
            int invCapacity,
            Collection<TestFn<I>> recipe,
            Collection<H> currentHeldItems,
            I item
    ) {
        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(GathererJournal.Item::isEmpty)) {
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
