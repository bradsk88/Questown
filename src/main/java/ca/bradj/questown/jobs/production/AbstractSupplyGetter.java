package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AbstractSupplyGetter<STATUS extends IProductionStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>, ROOM extends Room> {

    public void tryGetSupplies(
            STATUS status,
            int upToAmount,
            Map<STATUS, Collection<ROOM>> roomsNeedingIngredientsOrTools,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<STATUS, Collection<Predicate<TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker,
            Function<Predicate<TOWN_ITEM>, Predicate<TOWN_ITEM>> applySpecialRules
    ) {
        if (!status.isCollectingSupplies()) {
            return;
        }

        Optional<STATUS> first = roomsNeedingIngredientsOrTools.entrySet()
                .stream()
                .filter(v -> !v.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .findFirst();

        if (first.isEmpty()) {
            QT.JOB_LOGGER.warn("Trying to try container items when no rooms need items");
            return;
        }

        Collection<Predicate<TOWN_ITEM>> apply = new ArrayList<>(recipe.apply(first.get()));
        Predicate<TOWN_ITEM> originalTest = item -> JobsClean.shouldTakeItem(
                upToAmount, apply, currentHeldItems, item
        );
        Predicate<TOWN_ITEM> shouldTake = applySpecialRules.apply(originalTest);
        JobsClean.tryTakeContainerItems(taker, suppliesTarget, shouldTake::test);
    }

}
