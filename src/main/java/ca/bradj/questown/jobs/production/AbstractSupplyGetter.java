package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AbstractSupplyGetter<STATUS extends IStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>, ROOM extends Room> {

    public void tryGetSupplies(
            STATUS status,
            int upToAmount,
            Map<Integer, Collection<ROOM>> roomsNeedingIngredientsOrTools,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<Integer, Collection<JobsClean.TestFn<TOWN_ITEM>>> recipe,
            Collection<HELD_ITEM> currentHeldItems,
            Consumer<TOWN_ITEM> taker,
            Collection<String> statusRules,
            Predicate<TOWN_ITEM> isAnyWorkResult
    ) {
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

        Collection<Predicate<TOWN_ITEM>> apply = recipe.apply(first.get()).stream().map(
                v -> (Predicate<TOWN_ITEM>) v::test
        ).collect(Collectors.toList());
        Predicate<TOWN_ITEM> originalTest = item -> JobsClean.shouldTakeItem(
                upToAmount, apply, currentHeldItems, item
        );
        Predicate<TOWN_ITEM> shouldTake = originalTest;

        if (statusRules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
            shouldTake = item -> JobsClean.shouldTakeItem(
                    upToAmount, ImmutableList.of(isAnyWorkResult), currentHeldItems, item
            ) || originalTest.test(item);
        }

        JobsClean.tryTakeContainerItems(taker, suppliesTarget, shouldTake::test);
    }

}
