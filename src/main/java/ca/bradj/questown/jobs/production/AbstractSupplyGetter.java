package ca.bradj.questown.jobs.production;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.mc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.*;
import java.util.stream.Collectors;

public class AbstractSupplyGetter<STATUS extends IProductionStatus<?>, POS, TOWN_ITEM extends Item<TOWN_ITEM>, HELD_ITEM extends HeldItem<HELD_ITEM, TOWN_ITEM>> {

    public void tryGetSupplies(
            STATUS status,
            int upToAmount,
            Map<STATUS, ? extends Collection<?>> roomsWhereSuppliesCanBeUsed,
            JobsClean.SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<STATUS, Collection<BiPredicate<AmountHeld, TOWN_ITEM>>> recipe,
            Supplier<Collection<HELD_ITEM>> currentHeldItems,
            Consumer<TOWN_ITEM> taker
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
            Collection<BiPredicate<AmountHeld, TOWN_ITEM>> apply = new ArrayList<>(recipe.apply(s));
            Collection<? extends NoisyBiPredicate<AmountHeld, TOWN_ITEM>> recipe1 = Util.sameNoise(
                    apply,
                    "%s is an ingredient",
                    "%s is not an ingredient"
            );
            NoisyPredicate<TOWN_ITEM> originalTest = (item) -> JobsClean.shouldTakeItem(
                    upToAmount, recipe1, currentHeldItems.get(), item
            );
            if (JobsClean.tryTakeContainerItems(taker, suppliesTarget, Util.toQuiet(originalTest)::test)) {
                return;
            }
        }
    }

}
