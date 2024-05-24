package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.SpecialRuleIngredientAnyValidWorkOutput;
import ca.bradj.questown.jobs.SpecialRules;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TownContainerChecks {

    public static <ITEM extends Item<ITEM>, STATUS> boolean townHasSupplies(
            Collection<STATUS> statesWithRooms,
            Map<STATUS, ? extends Predicate<ITEM>> ingredientsRequiredAtStates,
            Map<STATUS, Integer> ingredientQtyRequiredAtStates,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInTown,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInInventory,
            Function<STATUS, ? extends Collection<String>> specialRules,
            BiPredicate<STATUS, ITEM> specialRuleAppliesToItem
    ) {
        boolean anyRequirementsExist = false;
        for (STATUS level : statesWithRooms) {
            Integer quantityRequired = ingredientQtyRequiredAtStates.get(level);
            if (quantityRequired == null) {
                continue;
            }
            anyRequirementsExist = true;
            Predicate<ITEM> isUsableItem = item -> {
                if (specialRuleAppliesToItem.test(level, item)) {
                    return true;
                }
                Predicate<ITEM> isWorkIngredient = ingredientsRequiredAtStates.get(level);
                if (isWorkIngredient == null) {
                    return false;
                }
                return isWorkIngredient.test(item);
            };
            if (TownContainerChecks.townHasSuppliesForStage(
                    pred -> {
                        ImmutableList.Builder<ITEM> b = ImmutableList.builder();
                        b.addAll(getItemMatchesInInventory.apply(pred));
                        b.addAll(getItemMatchesInTown.apply(pred));
                        return b.build();
                    },
                    isUsableItem,
                    quantityRequired,
                    specialRules.apply(level).contains(SpecialRules.INGREDIENTS_MUST_BE_SAME)
            )) {
                return true;
            }
        }
        return !anyRequirementsExist;
    }

    static <ITEM extends Item<ITEM>> boolean townHasSuppliesForStage(
            Function<Predicate<ITEM>, Collection<ITEM>> itemMatchesInTownOrHand,
            Predicate<ITEM> initialMatchCriteria,
            int quantityRequired,
            boolean mustAllBeSame
    ) {
        Collection<ITEM> matchingItems = itemMatchesInTownOrHand.apply(initialMatchCriteria);
        if (matchingItems.isEmpty()) {
            return false;
        }
        if (!mustAllBeSame) {
            return true;
        }
        for (ITEM i : matchingItems) {
            Collection<ITEM> sameAsI = itemMatchesInTownOrHand.apply(i::equals);
            Integer reduce = sameAsI.stream().map(Item::quantity).reduce(Integer::sum).orElse(0);
            if (reduce >= quantityRequired) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated Use the other signature
     */
    public static <ITEM extends Item<ITEM>, STATUS> boolean hasSupplies(
            Supplier<Set<STATUS>> roomsToGetSuppliesForByState,
            Map<STATUS, Predicate<ITEM>> ingredientsRequiredAtStates,
            Map<STATUS, Integer> ingredientQtyRequiredAtStates,
            Map<STATUS, PredicateCollection<ITEM>> toolsRequiredAtStates,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInTown,
            Function<STATUS, Collection<String>> specialRules,
            Predicate<ITEM> isWorkResult
    ) {
        return hasSupplies(
                roomsToGetSuppliesForByState,
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                toolsRequiredAtStates,
                getItemMatchesInTown,
                (i) -> ImmutableList.of(),
                specialRules,
                isWorkResult
        );
    }

    public static <ITEM extends Item<ITEM>, STATUS> boolean hasSupplies(
            Supplier<Set<STATUS>> roomsToGetSuppliesForByState,
            Map<STATUS, Predicate<ITEM>> ingredientsRequiredAtStates,
            Map<STATUS, Integer> ingredientQtyRequiredAtStates,
            Map<STATUS, PredicateCollection<ITEM>> toolsRequiredAtStates,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInTown,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInInventory,
            Function<STATUS, Collection<String>> specialRules,
            Predicate<ITEM> isWorkResult
    ) {
        Set<STATUS> statesWithRooms = roomsToGetSuppliesForByState.get();
        ImmutableMap.Builder<STATUS, Integer> oneTool = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> {
                    if (!v.isEmpty()) {
                        oneTool.put(k, 1);
                    }
                }
        );
        boolean townHasTools = TownContainerChecks.townHasTools(
                statesWithRooms,
                toolsRequiredAtStates,
                getItemMatchesInTown,
                getItemMatchesInInventory,
                specialRules
        );
        if (!townHasTools) {
            return false;
        }

        return TownContainerChecks.townHasSupplies(
                statesWithRooms,
                ingredientsRequiredAtStates,
                ingredientQtyRequiredAtStates,
                getItemMatchesInTown,
                getItemMatchesInInventory,
                specialRules,
                (lvl, item) -> SpecialRuleIngredientAnyValidWorkOutput.apply(
                        specialRules.apply(lvl),
                        i -> false,
                        isWorkResult
                ).test(item)
        );
    }

    public static <STATUS, ITEM extends Item<ITEM>> boolean townHasTools(
            Set<STATUS> statesWithRooms,
            Map<STATUS, PredicateCollection<ITEM>> toolsRequiredAtStates,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInTown,
            Function<Predicate<ITEM>, Collection<ITEM>> getItemMatchesInInventory,
            Function<STATUS, Collection<String>> specialRules
    ) {
        ImmutableMap.Builder<STATUS, Integer> oneTool = ImmutableMap.builder();
        toolsRequiredAtStates.forEach(
                (k, v) -> {
                    if (!v.isEmpty()) {
                        oneTool.put(k, 1);
                    }
                }
        );
        return townHasSupplies(
                statesWithRooms,
                toolsRequiredAtStates,
                oneTool.build(),
                getItemMatchesInTown,
                getItemMatchesInInventory,
                specialRules,
                (lvl, item) -> false
        );
    }
}
