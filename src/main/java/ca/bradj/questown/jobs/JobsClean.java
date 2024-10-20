package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.RoomsNeedingIngredientsOrTools;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            ImmutableList<? extends Predicate<TOWN_ITEM>> recipe
    ) {
        return journal.getItems().stream()
                      .filter(Predicates.not(Item::isEmpty))
                      .anyMatch(Predicates.not(v -> recipe.stream().anyMatch(z -> z.test(v.get()))));
    }

    @NotNull
    static <I extends Item<I>> ImmutableMap<Integer, Boolean> getSupplyItemStatuses(
            Supplier<Collection<I>> journal,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Function<Integer, Boolean> anyIngredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates,
            Function<Integer, Boolean> anyToolsRequiredAtStates,
            Map<Integer, Integer> workRequiredAtStates,
            int maxState
    ) {
        if (journal.get().stream().allMatch(Item::isEmpty)) {
            ImmutableMap.Builder<Integer, Boolean> b = ImmutableMap.builder();
            for (int i = 0; i < maxState; i++) {
                b.put(i, false);
            }
            return b.build();
        }

        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, Predicate<I>> fn = (state, ingr) -> {
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
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        for (Map.Entry<Integer, Integer> work : workRequiredAtStates.entrySet()) {
            // If work is require, but no tools or items are required: pretend we have the necessary supply items
            if (!anyIngredientsRequiredAtStates.apply(work.getKey()) && !anyToolsRequiredAtStates.apply(work.getKey())) {
                b.put(work.getKey(), true);
            }
        }
        return ImmutableMap.copyOf(b);
    }

    public static <I extends Item<I>> boolean hasNonSupplyItems(
            Collection<I> items,
            int state,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates
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

    private static <I> boolean isNotToolFromAnyStage(
            I i,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates
    ) {
        for (Predicate<I> e : toolsRequiredAtStates.values()) {
            if (e.test(i)) {
                return false;
            }
        }
        // Item is not a tool
        return true;
    }

    public static <
            I extends Item<I>,
            H extends HeldItem<H, I>
            > boolean shouldTakeItem(
            int invCapacity,
            Collection<? extends Predicate<I>> neededItemsIn,
            Collection<H> currentHeldItems,
            I item
    ) {
        if (neededItemsIn.isEmpty()) {
            return false;
        }

        // Check if all items in the inventory are empty
        if (currentHeldItems.stream().noneMatch(Item::isEmpty)) {
            return false;
        }

        ArrayList<Predicate<I>> neededItems = new ArrayList<>(neededItemsIn);

        removeItemsAlreadyHeld(neededItems, currentHeldItems);
        return isItemNeeded(neededItems, item);
    }

    private static <I extends Item<I>> boolean isItemNeeded(
            Collection<Predicate<I>> itemsNeeded,
            I item
    ) {
        for (Predicate<I> ingredient : itemsNeeded) {
            if (ingredient.test(item)) {
                return true;
            }
        }
        return false;
    }

    private static <I extends Item<I>, H extends HeldItem<H, I>> void removeItemsAlreadyHeld(
            ArrayList<Predicate<I>> ingredientsToSatisfy,
            Collection<H> currentHeldItems
    ) {
        ArrayList<H> heldItemsToCheck = new ArrayList<>(currentHeldItems);
        for (int i = 0; i < ingredientsToSatisfy.size(); i++) {
            for (H heldItem : heldItemsToCheck) {
                if (ingredientsToSatisfy.get(i).test(heldItem.get())) {
                    ingredientsToSatisfy.remove(i);
                    i--;
                    heldItemsToCheck.remove(heldItem);
                    break;
                }
            }
        }
    }

    public static <ROOM, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> ImmutableList<MATCH> roomsWithState(
            Collection<MATCH> rooms,
            Predicate<POS> isCorrectBlock,
            Predicate<POS> hasCorrectState
    ) {
        @NotNull List<WithReason<@Nullable MATCH>> filtered = filterByPredicates(
                rooms,
                isCorrectBlock,
                hasCorrectState
        );
        List<MATCH> values = filtered.stream().map(v -> v.value).filter(Objects::nonNull).toList();
        return ImmutableList.copyOf(values);
    }

    private static <ROOM, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>> @NotNull List<WithReason<@Nullable MATCH>> filterByPredicates(
            Collection<MATCH> rooms,
            Predicate<POS> isCorrectBlock,
            Predicate<POS> hasCorrectState
    ) {
        ArrayList<WithReason<@Nullable MATCH>> out = new ArrayList<>();
        for (MATCH room : rooms) {
            out.add(describeFilteredRoom(room, isCorrectBlock, hasCorrectState));
        }
        return out;
    }

    private static <MATCH extends IRoomRecipeMatch<?, ?, POS, ?>, POS> WithReason<@Nullable MATCH> describeFilteredRoom(
            MATCH room,
            Predicate<POS> isCorrectBlock,
            Predicate<POS> hasCorrectState
    ) {
        Map<POS, ?> allContainedBlocks = room.getContainedBlocks();
        HashSet<POS> allUniquePos = new HashSet<>(allContainedBlocks.keySet());
        List<WithReason<POS>> allTestedPos = new ArrayList<>();
        for (POS p : allUniquePos) {
            if (isCorrectBlock.test(p)) {
                allTestedPos.add(WithReason.always(p, "is correct block: " + isCorrectBlock));
                continue;
            }
            allTestedPos.add(WithReason.always(null, "is not correct block: " + isCorrectBlock));
        }
        if (allTestedPos.isEmpty()) {
            return WithReason.always(null, "None of the blocks are job blocks");
        }
        List<POS> allTestedNonNullPos = allTestedPos.stream().filter(v -> v.value != null).map(v -> v.value).toList();
        for (POS e : allTestedNonNullPos) {
            if (e == null) {
                continue;
            }
            if (hasCorrectState.test(e)) {
                return WithReason.always(room, e + " had correct state");
            }
        }
        return WithReason.always(
                null,
                "None of the job blocks has correct state: [" +
                        String.join(", ", allTestedNonNullPos.stream().map(Object::toString).toList()) + "]"
        );
    }

    // TODO[ASAP]: Test "should not return null if entity is in room with finished product"
    public static <ROOM extends Room, RECIPE, POS, MATCH extends IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> MATCH getEntityCurrentJobSite(
            Position entityBlockPos,
            RoomsNeedingIngredientsOrTools<ROOM, RECIPE, POS> roomsNeedingIngredientsOrTools,
            Collection<MATCH> roomsWithCompletedProduct,
            Predicate<ROOM> additionalPosCheck
    ) {
        for (MATCH room : roomsWithCompletedProduct) {
            if (InclusiveSpaces.contains(room.getRoom().getSpaces(), entityBlockPos)) {
                return room;
            }
        }

        // TODO: Support multiple tiers of job site (i.e. more than one resource location)
        Predicate<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> containsEntity = v ->
        {
            boolean contains = InclusiveSpaces.contains(v.getRoom().getSpaces(), entityBlockPos);
            return contains || v.getRoom().getDoorPos().equals(entityBlockPos);
        };
        //noinspection unchecked
        return (MATCH) roomsNeedingIngredientsOrTools
                .getMatches()
                .stream()
                .filter(v -> additionalPosCheck.test(v.getRoom()))
                .filter(containsEntity)
                .findFirst()
                .orElse(null);
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

    public static <POS, TOWN_ITEM extends Item<TOWN_ITEM>> void tryTakeContainerItems(
            Consumer<TOWN_ITEM> villager,
            SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<TOWN_ITEM, Boolean> isRemovalCandidate
    ) {
        if (!suppliesTarget.isCloseTo()) {
            return;
        }
        String start = suppliesTarget.toShortString();
        List<TOWN_ITEM> items = suppliesTarget.getItems();
        for (int i = 0; i < items.size(); i++) {
            TOWN_ITEM mcTownItem = items.get(i);
            if (isRemovalCandidate.apply(mcTownItem)) {
                TOWN_ITEM unit = mcTownItem.unit();
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", unit.getShortName(), start);
                villager.accept(unit);
                suppliesTarget.removeItem(i, 1);
                break;
            }
        }
    }
}
