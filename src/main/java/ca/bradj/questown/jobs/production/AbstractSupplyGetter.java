package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobsClean;
import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbstractSupplyGetter<STATUS extends IStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>, ROOM extends Room> {

    public void tryGetSupplies(
            STATUS status,
            int upToAmount,
            Map<Integer, Collection<ROOM>> roomsNeedingIngredientsOrTools,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<Integer, Collection<JobsClean.TestFn<TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker) {
        // TODO: Introduce this status for farmer
        if (!status.isCollectingSupplies()) {
            return;
        }

        Optional<Integer> first = roomsNeedingIngredientsOrTools.entrySet()
                .stream()
                .filter(v -> !v.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (first.isEmpty()) {
            QT.JOB_LOGGER.warn("Trying to try container items when no rooms need items");
            return;
        }

        JobsClean.<POS, TOWN_ITEM>tryTakeContainerItems(
                taker, suppliesTarget,
                item -> JobsClean.<TOWN_ITEM, HELD_ITEM>shouldTakeItem(
                        upToAmount, recipe.apply(first.get()), currentHeldItems, item
                )
        );
    }

}
