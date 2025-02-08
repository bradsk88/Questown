package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobsClean;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class AbstractSupplyGetter<STATUS extends IStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>, ROOM extends Room> {

    public boolean tryGetSupplies(
            STATUS status,
            int upToAmount,
            RoomsNeedingIngredientsOrTools<?, ?, ?> roomsNeedingIngredientsOrTools,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<Integer, Collection<? extends IPredicateCollection<TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker
    ) {
        return tryGetSupplies(
                status, upToAmount, roomsNeedingIngredientsOrTools,
                suppliesTarget, recipe, currentHeldItems, taker,
                UtilClean::enumerate
        );
    }
    public boolean tryGetSupplies(
            STATUS status,
            int upToAmount,
            RoomsNeedingIngredientsOrTools<?, ?, ?> roomsNeedingIngredientsOrTools,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<Integer, Collection<? extends IPredicateCollection<TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker,
            Function<List<TOWN_ITEM>, List<Pair<Integer, TOWN_ITEM>>> adjustOrder
    ) {
        // TODO: Introduce this status for farmer
        if (!status.isCollectingSupplies()) {
            return false;
        }

        Optional<Integer> first = roomsNeedingIngredientsOrTools.get().entrySet()
                .stream()
                .filter(v -> !v.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (first.isEmpty()) {
            QT.JOB_LOGGER.warn("Trying to try container items when no rooms need items");
            return false;
        }

        return JobsClean.<POS, TOWN_ITEM>tryTakeContainerItems(
                taker, suppliesTarget,
                item -> JobsClean.<TOWN_ITEM, HELD_ITEM>shouldTakeItem(
                        upToAmount, recipe.apply(first.get()), currentHeldItems, item
                ),
                adjustOrder
        );
    }

}
