package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.registries.tags.ITag;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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

        Predicate<TOWN_ITEM> originalTest = item -> JobsClean.shouldTakeItem(
                upToAmount, recipe.apply(first.get()), currentHeldItems, item
        );
        Predicate<TOWN_ITEM> shouldTake = originalTest;

        if (statusRules.contains(SpecialRules.INGREDIENT_ANY_VALID_WORK_OUTPUT)) {
            shouldTake = item -> JobsClean.shouldTakeItem(
                    upToAmount, ImmutableList.of(isAnyWorkResult::test), currentHeldItems, item
            ) || originalTest.test(item);
        }

        JobsClean.tryTakeContainerItems(taker, suppliesTarget, shouldTake::test);
    }

}
