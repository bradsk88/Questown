package ca.bradj.questown.jobs.special;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class IngredientsFromHeldItemLogic {
    public static <ITEM, REQ>  boolean ingredientsExistForHoldableItems(
            Collection<? extends Supplier<? extends Collection<ITEM>>> containersInRoom,
            Collection<? extends Supplier<? extends Collection<ITEM>>> allOtherContainersInTown,
            Predicate<ITEM> isItemHoldable,
            Function<ITEM, REQ> getRequest,
            BiPredicate<ITEM, REQ> itemSatisfiesRequest
    ) {
        for (Supplier<? extends Collection<ITEM>> container : containersInRoom) {
            for (ITEM item : container.get()) {
                if (!isItemHoldable.test(item)) {
                    continue;
                }
                REQ request = getRequest.apply(item);
                if (ingredientsExist(request, itemSatisfiesRequest, allOtherContainersInTown)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static <REQ, ITEM> boolean ingredientsExist(
            REQ request,
            BiPredicate<ITEM, REQ> itemSatisfiesRequest,
            Collection<? extends Supplier<? extends Collection<ITEM>>> allOtherContainersInTown
    ) {
        for (Supplier<? extends Collection<ITEM>> container : allOtherContainersInTown) {
            for (ITEM item : container.get()) {
                if (itemSatisfiesRequest.test(item, request)) {
                    return true;
                }
            }
        }
        return false;
    }
}
