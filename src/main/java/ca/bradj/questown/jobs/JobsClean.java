package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput.NVIRoom;
import ca.bradj.questown.town.workstatus.State;
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
import java.util.stream.Stream;

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
    static <I extends Item<I>> ImmutableMap<Integer, SupplyItemStatus> getSupplyItemStatuses(
            Supplier<Collection<I>> journal,
            Map<Integer, ? extends Predicate<I>> ingredientsRequiredAtStates,
            Function<Integer, Boolean> anyIngredientsRequiredAtStates,
            Map<Integer, ? extends Predicate<I>> toolsRequiredAtStates,
            Function<Integer, Boolean> anyToolsRequiredAtStates,
            Map<Integer, Integer> workRequiredAtStates,
            int maxState
    ) {
        HashMap<Integer, SupplyItemStatus> b = new HashMap<>();
        BiConsumer<Integer, Predicate<I>> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, SupplyItemStatus.NOT_REQUIRED);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean hasItem = journal.get().stream().anyMatch(ingr);
            boolean neededOrUnknown = b.getOrDefault(state, SupplyItemStatus.NEEDS_ITEM) == SupplyItemStatus.NEEDS_ITEM;
            if (neededOrUnknown) {
                b.put(state, hasItem ? SupplyItemStatus.HAS_ITEM : SupplyItemStatus.NEEDS_ITEM);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        for (Map.Entry<Integer, Integer> work : workRequiredAtStates.entrySet()) {
            if (!anyIngredientsRequiredAtStates.apply(work.getKey()) && !anyToolsRequiredAtStates.apply(work.getKey())) {
                b.put(work.getKey(), SupplyItemStatus.NOT_REQUIRED);
            }
        }
        for (int i = 0; i < maxState; i++) {
            fn.accept(i, null);
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

    // TODO: Test "should not return null if entity is in room with finished product"
    public static <ROOM extends Room, RECIPE, POS> EntityCurrentJobSite<ROOM> getEntityCurrentJobSite(
            // TODO: Consider y coordinate
            Position entityBlockPos,
            RoomsNeedingVillagerInput<ROOM, RECIPE, POS> roomsNeedingIngredientsOrTools,
            Collection<ROOM> roomsWithCompletedProduct,
            Predicate<ROOM> additionalPosCheck,
            Predicate<RECIPE> isFarm
    ) {
        for (ROOM room : roomsWithCompletedProduct) {
            if (InclusiveSpaces.contains(room.getSpaces(), entityBlockPos)) {
                return new EntityCurrentJobSite<>(room, false); // TODO: Add a check for farm
            }
        }

        // TODO: Support multiple tiers of job site (i.e. more than one resource location)
        Predicate<IRoomRecipeMatch<ROOM, RECIPE, POS, ?>> containsEntity = v ->
        {
            boolean contains = InclusiveSpaces.contains(v.getRoom().getSpaces(), entityBlockPos);
            return contains || v.getRoom().getDoorPos().equals(entityBlockPos);
        };
        // FIXME: We should also check rooms needing WORK
        return roomsNeedingIngredientsOrTools
                .getMatches()
                .stream()
                .map(RoomsNeedingVillagerInput.NVIRoom::room)
                .filter(v -> additionalPosCheck.test(v.getRoom()))
                .filter(containsEntity)
                .findFirst()
                .map(v -> new EntityCurrentJobSite<>(v.getRoom(), v.getRecipeIDs().stream().anyMatch(isFarm)))
                .orElse(null);
    }

    public static <ROOM extends Room, RECIPE, BLOCK> @NotNull WithReason<@Nullable BLOCK> findJobSite(
            int maxState,
            boolean prioritizeExtraction,
            Map<Integer, SupplyItemStatus> statusItems,
            Collection<ROOM> roomsWithFinishedProduct,
            Function<ROOM, BLOCK> getPositionWithin,
            RoomsNeedingVillagerInput<ROOM, RECIPE, BLOCK> blocksSrc,
            Function<BLOCK, State> work,
            Predicate<BLOCK> isJobBlock,
            BiFunction<BLOCK, ROOM, BLOCK> findInteractionSpot
    ) {
        if (prioritizeExtraction && !roomsWithFinishedProduct.isEmpty()) {
            return WithReason.always(
                    getPositionWithin.apply(roomsWithFinishedProduct.iterator().next()),
                    "prioritizing extraction and room has result"
            );
        }

        ArrayList<NVIRoom<ROOM, RECIPE, BLOCK>> rooms = new ArrayList<>(blocksSrc.getMatches());
        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        boolean roomFoundButNotBlock = true;

        for (NVIRoom<ROOM, RECIPE, BLOCK> match : rooms) {
            for (Map.Entry<BLOCK, ?> blocks : match.room().getContainedBlocks().entrySet()
            ) {
                BLOCK blockPos = blocks.getKey();
                State blockState = work.apply(blockPos);
                if (blockState == null) {
                    blockState = State.freshAtState(0);
                }
                if (isJobBlock.test(blockPos)) {
                    roomFoundButNotBlock = false;
                } else {
                    continue;
                }

                Supplier<BLOCK> is = () -> findInteractionSpot.apply(
                        blockPos,
                        match.room().getRoom()
                );

                if (maxState == blockState.processingState()) {
                    return new WithReason<>(is.get(), "Found extractable product");
                }
                SupplyItemStatus sis = statusItems.getOrDefault(blockState.processingState(), SupplyItemStatus.NOT_REQUIRED);
                if (sis != SupplyItemStatus.NEEDS_ITEM) {
                    return new WithReason<>(is.get(), "Found a spot where a held item can be used");
                }
            }
        }

        if (roomFoundButNotBlock) {
            return new WithReason<>(null, "Job site found, but no usable job blocks");
        }

        return new WithReason<>(null, "No job sites");
    }

    public static <POS, ROOM extends Room, RECIPE> ImmutableMap<Integer, RoomsWithWorkableStatefulBlocks<POS>> rooms(
            Supplier<ImmutableList<NVIRoom<ROOM, RECIPE, POS>>> jobSites,
            Function<POS, State> jobBlockStates,
            Predicate<POS> isJobBlock,
            Function<POS, String> stringify,
            int maxState
    ) {
        ImmutableMap.Builder<Integer, RoomsWithWorkableStatefulBlocks<POS>> b = ImmutableMap.builder();
        Supplier<Rooms<POS, ?>> e = () -> {
            ImmutableMap.Builder<POS, Integer> spotStatuses = ImmutableMap.builder();
            ImmutableMap.Builder<POS, Boolean> spotJBs = ImmutableMap.builder();
            Map<ROOM, List<Integer>> roomStatuses = new HashMap<>();
            Stream<NVIRoom<ROOM, RECIPE, POS>> rooms = jobSites.get().stream();

            //TODO: Validate that this is actually needed
            rooms = rooms.filter(v -> !v.dueToWorkOnly());

            rooms.forEach(match -> {
                for (Map.Entry<POS, ?> entry : match.room().getContainedBlocks().entrySet()) {
                    POS bp = entry.getKey();
                    State jobBlockState = jobBlockStates.apply(bp);
                    if (jobBlockState == null) {
                        continue;
                    }
                    int v = jobBlockState.processingState();
                    spotStatuses.put(bp, v);
                    UtilClean.addOrInitializeList(roomStatuses, match.room().getRoom(), v);
                    spotJBs.put(bp, isJobBlock.test(bp));
                }
            });
            return new Rooms<>(spotStatuses.build(), roomStatuses, spotJBs.build());
        };

        for (int i = 0; i < maxState; i++) {
            b.put(i, new RoomsWithWorkableStatefulBlocks<>(i, e, stringify));
        }
        return b.build();
    }

    public static <POS, MATCH extends IRoomRecipeMatch<?, ?, POS, ?>> boolean isUnfinishedTimeWorkPresent(
            Supplier<ImmutableList<MATCH>> roomSource,
            Function<POS, Integer> ticksSource
    ) {
        ImmutableList<MATCH> rooms = roomSource.get();
        return rooms.stream()
                    .anyMatch(v -> {
                        for (Map.Entry<POS, ?> e : v.getContainedBlocks().entrySet()) {
                            @Nullable Integer apply = ticksSource.apply(e.getKey());
                            if (apply != null && apply > 0) {
                                return true;
                            }
                        }
                        return false;
                    });
    }

    public static <POS> Collection<Integer> getStatesWithUnfinishedWork(
            Collection<? extends Supplier<Collection<POS>>> rooms,
            Function<POS, State> getJobBlockState,
            Predicate<POS> canClaim
    ) {
        HashSet<Integer> b = new HashSet<>();
        rooms.forEach(v -> {
            for (POS e : v.get()) {
                if (!canClaim.test(e)) {
                    continue;
                }
                @Nullable State apply = getJobBlockState.apply(e);
                if (apply != null && apply.workLeft() > 0) {
                    b.add(apply.processingState());
                    return;
                }
            }
        });
        ArrayList<Integer> b2 = new ArrayList<>(b);
        Collections.sort(b2);
        return ImmutableList.copyOf(b2);
    }

    public interface SuppliesTarget<POS, TOWN_ITEM> {

        boolean isCloseTo();

        String toShortString();

        List<TOWN_ITEM> getItems();

        void removeItem(
                int i
        );
    }

    public static <POS, TOWN_ITEM extends Item<TOWN_ITEM>> boolean tryTakeContainerItems(
            Consumer<TOWN_ITEM> villager,
            SuppliesTarget<POS, TOWN_ITEM> suppliesTarget,
            Function<TOWN_ITEM, Boolean> isRemovalCandidate,
            Function<List<TOWN_ITEM>, List<Pair<Integer, TOWN_ITEM>>> adjustOrder
    ) {
        if (!suppliesTarget.isCloseTo()) {
            return false;
        }
        String start = suppliesTarget.toShortString();
        List<Pair<Integer, TOWN_ITEM>> items = adjustOrder.apply(suppliesTarget.getItems());
        for (Pair<Integer, TOWN_ITEM> mcTownItem : items) {
            if (isRemovalCandidate.apply(mcTownItem.b())) {
                TOWN_ITEM unit = mcTownItem.b().unit();
                QT.JOB_LOGGER.debug("Villager is taking {} from {}", unit.getShortName(), start);
                villager.accept(unit);
                suppliesTarget.removeItem(mcTownItem.a());
                return true;
            }
        }
        return false;
    }
}
