package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobsClean;
import ca.bradj.roomrecipes.core.Room;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class AbstractSupplyGetter<STATUS extends IProductionStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>> {

    public void tryGetSupplies(
            STATUS status,
            int upToAmount,
            Map<STATUS, ? extends Collection<?>> roomsWhereSuppliesCanBeUsed,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<STATUS, Collection<BiPredicate<Integer, TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker,
            BiFunction<Integer, BiPredicate<Integer, TOWN_ITEM>, BiPredicate<Integer, TOWN_ITEM>> applySpecialRules
    ) {
        if (!status.isCollectingSupplies()) {
            return;
        }

        Set<STATUS> rooms = roomsWhereSuppliesCanBeUsed.entrySet()
                                                       .stream()
                                                       .filter(v -> !v.getValue().isEmpty())
                                                       .map(Map.Entry::getKey)
                                                       .collect(Collectors.toSet());

        if (rooms.isEmpty()) {
            QT.JOB_LOGGER.warn("Trying to try container items when no rooms need items");
            return;
        }

        for (STATUS s : rooms) {
            Collection<BiPredicate<Integer, TOWN_ITEM>> apply = new ArrayList<>(recipe.apply(s));
            BiPredicate<Integer, TOWN_ITEM> originalTest = (held, item) -> JobsClean.shouldTakeItem(
                    upToAmount, apply, currentHeldItems, item
            );
            BiPredicate<Integer, TOWN_ITEM> shouldTake = applySpecialRules.apply(s.value(), originalTest);
            if (JobsClean.tryTakeContainerItems(taker, suppliesTarget, i -> shouldTake.test(0, i))) {
                return;
            }
        }
    }

}
