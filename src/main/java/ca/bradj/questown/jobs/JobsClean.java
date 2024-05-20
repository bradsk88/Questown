package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

public class JobsClean {

    public static <STATUS extends IStatus<STATUS>> WithReason<STATUS> doOrGoTo(
            STATUS status,
            boolean isAtJobSite,
            STATUS goStatus
    ) {
        if (isAtJobSite) {
            return new WithReason<>(status, "Villager is not at job site");
        }
        return new WithReason<>(goStatus, "Already in job site");
    }

    public static <HELD extends HeldItem<HELD, TOWN_ITEM>, TOWN_ITEM extends Item<TOWN_ITEM>> boolean hasNonSupplyItems(
            ItemsHolder<HELD> journal,
            ImmutableList<TestFn<TOWN_ITEM>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(AmountHeld.none(), v.get()))));
    }

    @NotNull
    static <I> ImmutableMap<Integer, Boolean> getSupplyItemStatuses(
            Supplier<Collection<I>> journal,
            Map<Integer, Predicate<I>> ingredientsRequiredAtStates,
            Map<Integer, Predicate<I>> toolsRequiredAtStates,
            int maxState,
            boolean toolsOnly,
            Map<Integer, Predicate<I>> statesWhereSpecialRulesCreateWork
    ) {
        ImmutableMap.Builder<Integer, Predicate<I>> b = ImmutableMap.builder();
        if (!toolsOnly) {
            statesWhereSpecialRulesCreateWork.forEach((k, v) -> // FIXME: Test this
                    b.put(k,
                            item -> v.test(item) || Util.getOrDefault(ingredientsRequiredAtStates, k, (I z) -> false)
                                                        .test(item)
                    ));
        }

        return JobsClean.getSupplyItemStatuses(
                journal,
                b.build(),
                toolsRequiredAtStates,
                maxState
        );
    }

    @NotNull
    static <I> ImmutableMap<Integer, Boolean> getSupplyItemStatuses(
            Supplier<Collection<I>> journal,
            Map<Integer, Predicate<I>> ingredientsRequiredAtStates,
            Map<Integer, Predicate<I>> toolsRequiredAtStates,
            int maxState
    ) {
        HashMap<Integer, Boolean> b = new HashMap<>();
        boolean prevToolStatus = true;
        for (int i = 0; i <= maxState; i++) {
            Predicate<I> ingr = ingredientsRequiredAtStates.get(i);
            Predicate<I> tool = toolsRequiredAtStates.get(i);
            if (ingr == null && tool == null) {
                b.put(i, prevToolStatus);
                continue;
            }
            if (ingr == null) {
                b.put(i, journal.get().stream().anyMatch(tool));
                continue;
            }
            b.put(i, journal.get().stream().anyMatch(ingr));
        }
        ingredientsRequiredAtStates.forEach((state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journal.get().stream().anyMatch(ingr);
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        });
        toolsRequiredAtStates.forEach((state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            boolean has = journal.get().stream().anyMatch(ingr);
            if (!has) {
                for (int i = state; i < maxState; i++) {
                    b.put(i, false);
                }
            } else {
                if (!b.getOrDefault(state, false)) {
                    b.put(state, true);
                }
            }
        });
        return ImmutableMap.copyOf(b);
    }

    public static <I extends Item<I>> WithReason<Boolean> hasNonSupplyItems(
            Collection<I> items,
            int state,
            ImmutableMap<Integer, Predicate<I>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Predicate<I>> toolsRequiredAtStates
    ) {
        if (items.isEmpty()) {
            return new WithReason<>(false, "Inventory is empty");
        }

        if (items.stream().allMatch(Item::isEmpty)) {
            return new WithReason<>(false, "Inventory is full of air");
        }

        items = items.stream().filter(v -> !v.isEmpty()).toList();

        Predicate<I> ings = ingredientsRequiredAtStates.get(state);
        if (ings == null) {
            Util.anyMatch(items.stream(), i -> isNotToolFromAnyStage(i, toolsRequiredAtStates));;
        }
        return Util.anyMatch(items.stream(), v -> {
            if (ings.test(v)) {
                return new WithReason<>(false, "%s is a required ingredient at state %d", v.getShortName(), state);
            }
            return new WithReason<>(true, "%s is NOT a required ingredient at state %d", v.getShortName(), state);
        });
    }


    @NotNull
    private static <I extends Item<I>> WithReason<Boolean> isNotToolFromAnyStage(
            I i,
            ImmutableMap<Integer, Predicate<I>> toolsRequiredAtStates
    ) {
        for (Predicate<I> e : toolsRequiredAtStates.values()) {
            if (e.test(i)) {
                return new WithReason<>(false, "%s is a required tool", i.getShortName());
            }
        }
        return new WithReason<>(true, "%s is not a required tool", i.getShortName());
    }

    public static <ROOM, POS, BLOCK> ImmutableList<RoomWithBlocks<ROOM, POS, BLOCK>> roomsWithState(
            Collection<? extends RoomWithBlocks<ROOM, POS, BLOCK>> rooms,
            Predicate<POS> hasState
    ) {
        ImmutableList.Builder<RoomWithBlocks<ROOM, POS, BLOCK>> b = ImmutableList.builder();

        rooms.stream()
             .filter(v -> {
                 for (Map.Entry<POS, ?> e : v.containedBlocks.entrySet()) {
                     if (hasState.test(e.getKey())) {
                         return true;
                     }
                 }
                 return false;
             })
             .forEach(b::add);
        return b.build();
    }

    public interface TestFn<I extends Item<I>> {
        boolean test(AmountHeld held, I item);
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>
            > WithReason<Boolean> shouldTakeItem(
            int invCapacity,
            Collection<? extends NoisyBiPredicate<AmountHeld, I>> recipe,
            Collection<H> currentHeldItems,
            I item
    ) {
        if (recipe.isEmpty()) {
            return new WithReason<>(false, "There are no active item requirements");
        }

        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(Item::isEmpty)) {
            return new WithReason<>(false, "There is no room for more items");
        }

        ArrayList<H> heldItemsToCheck = new ArrayList<>(currentHeldItems);

        ImmutableList<NoisyBiPredicate<AmountHeld, I>> initial = ImmutableList.copyOf(recipe);
        ArrayList<NoisyBiPredicate<AmountHeld, I>> ingredientsToSatisfy = new ArrayList<>();
        ingredientsToSatisfy.addAll(initial);

        AmountHeld amountHeld = AmountHeld.none();
        for (int i = 0; i < ingredientsToSatisfy.size(); i++) {
            for (H heldItem : heldItemsToCheck) {
                AmountHeld numHeld = AmountHeld.none(); // We do not need to check if we can add it to our "amount held"
                WithReason<Boolean> test = ingredientsToSatisfy.get(i).test(numHeld, heldItem.get());
                if (test.value) {
                    ingredientsToSatisfy.remove(i);
                    i--;
                    heldItemsToCheck.remove(heldItem);
                    amountHeld = amountHeld.up();
                    break;
                }
            }
        }

        for (NoisyBiPredicate<AmountHeld, I> ingredient : ingredientsToSatisfy) {
            WithReason<Boolean> test = ingredient.test(amountHeld, item);
            if (test.value) {
                return test;
            }
        }
        return new WithReason<>(false, "No items match current job requirements");
    }

    public interface SuppliesTarget<POS, TOWN_ITEM> {

        boolean isCloseTo();

        String toShortString();

        List<TOWN_ITEM> getItems();

        void removeItem(
                int i,
                int quantity
        );
    }

    public static <POS, TOWN_ITEM extends Item<TOWN_ITEM>> boolean tryTakeContainerItems(
            Consumer<TOWN_ITEM> villager,
            SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<TOWN_ITEM, Boolean> isRemovalCandidate
    ) {
        if (!suppliesTarget.isCloseTo()) {
            return false;
        }
        String start = suppliesTarget.toShortString();
        List<TOWN_ITEM> items = suppliesTarget.getItems();
        for (int i = 0; i < items.size(); i++) {
            TOWN_ITEM mcTownItem = items.get(i);
            if (isRemovalCandidate.apply(mcTownItem)) {
                TOWN_ITEM unit = mcTownItem.unit();
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", unit, start);
                villager.accept(unit);
                suppliesTarget.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }
}
