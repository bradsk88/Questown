package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;

public class JobsClean {

    public static <STATUS extends IStatus<STATUS>> STATUS doOrGoTo(
            STATUS status,
            boolean isAtJobSite,
            STATUS goStatus
    ) {
        if (isAtJobSite) {
            return status;
        }
        return goStatus;
    }

    public static <HELD extends HeldItem<HELD, TOWN_ITEM>, TOWN_ITEM extends Item<TOWN_ITEM>> boolean hasNonSupplyItems(
            ItemsHolder<HELD> journal,
            ImmutableList<TestFn<TOWN_ITEM>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(v.get()))));
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

    public static <I extends Item<I>> boolean hasNonSupplyItems(
            Collection<I> items,
            int state,
            ImmutableMap<Integer, Predicate<I>> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Predicate<I>> toolsRequiredAtStates
    ) {
        if (items.isEmpty() || items.stream().allMatch(Item::isEmpty)) {
            return false;
        }

        items = items.stream().filter(v -> !v.isEmpty()).toList();

        Predicate<I> ings = ingredientsRequiredAtStates.get(state);
        if (ings == null) {
            return items.stream().anyMatch(
                    i -> isNotToolFromAnyStage(i, toolsRequiredAtStates)
            );
        }
        return items.stream().anyMatch(v -> !ings.test(v));
    }


    @NotNull
    private static <I> boolean isNotToolFromAnyStage(
            I i,
            ImmutableMap<Integer, Predicate<I>> toolsRequiredAtStates
    ) {
        for (Predicate<I> e : toolsRequiredAtStates.values()) {
            if (e.test(i)) {
                return false;
            }
        }
        // Item is not a tool
        return true;
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
        boolean test(I item);
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>
            > boolean shouldTakeItem(
            int invCapacity,
            Collection<BiPredicate<Integer, I>> recipe,
            Collection<H> currentHeldItems,
            I item
    ) {
        if (recipe.isEmpty()) {
            return false;
        }

        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(Item::isEmpty)) {
            return false;
        }

        ArrayList<H> heldItemsToCheck = new ArrayList<>(currentHeldItems);

        ImmutableList<BiPredicate<Integer, I>> initial = ImmutableList.copyOf(recipe);
        ArrayList<BiPredicate<Integer, I>> ingredientsToSatisfy = new ArrayList<>();
        ingredientsToSatisfy.addAll(initial);

        int held = 0;
        for (int i = 0; i < ingredientsToSatisfy.size(); i++) {
            for (H heldItem : heldItemsToCheck) {
                if (ingredientsToSatisfy.get(i).test(0, heldItem.get())) {
                    ingredientsToSatisfy.remove(i);
                    i--;
                    heldItemsToCheck.remove(heldItem);
                    held++;
                    break;
                }
            }
        }

        for (BiPredicate<Integer, I> ingredient : ingredientsToSatisfy) {
            if (ingredient.test(held, item)) {
                return true;
            }
        }
        return false;
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
