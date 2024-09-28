package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class JobStatuses {

    public static boolean hasItems(EntityInvStateProvider<?> inventory) {
        if (inventory.hasNonSupplyItems()) {
            return true;
        }
        return inventory.getSupplyItemStatus().values().stream().anyMatch(Boolean::booleanValue);
    }

    public interface Job<STATUS, SUP_CAT> {
        @Nullable
        STATUS tryChoosingItemlessWork();

        @Nullable
        STATUS tryUsingSupplies(Map<SUP_CAT, Boolean> supplyItemStatus);
    }

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        Map<SUP_CAT, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();

        // TODO: Move "reason" out to dependency
        LZCD<LZCD.Dependency<STATUS>> dHasWorkItems = prePopAble(
                "hasWorkItems",
                () -> supplyItemStatus.containsValue(true)
        );
        LZCD<LZCD.Dependency<STATUS>> dHasNonWorkItems = prePopAble(
                "hasNonWorkItems",
                inventory::hasNonSupplyItems
        );
        LZCD<LZCD.Dependency<STATUS>> dHasAnyItems = prePopAble(
                "hasAnyItems",
                () -> supplyItemStatus.containsValue(true) || inventory.hasNonSupplyItems()
        );
        LZCD<LZCD.Dependency<STATUS>> dInventoryEmpty = prePopAble(
                "inventory empty",
                () -> !supplyItemStatus.containsValue(true) || !inventory.hasNonSupplyItems()
        );
        LZCD<LZCD.Dependency<STATUS>> dInventoryFull = prePopAble(
                "inventory full",
                inventory::inventoryFull
        );
        ILZCD<LZCD.Dependency<STATUS>> dPrioritizeExtraction = prePopAble(
                "prioritizing extraction",
                () -> prioritizeExtraction
        );
        ILZCD<LZCD.Dependency<STATUS>> dStatusNotGoing = input(
                "not going to jobsite",
                s -> !factory.goingToJobSite().equals(s)
        );
        ILZCD<LZCD.Dependency<STATUS>> dTownHasSpace = prePopAble(
                "town has space",
                town::hasSpace
        );
        ILZCD<LZCD.Dependency<STATUS>> dTimerActive = prePopAble(
                "town has active timer for this job",
                town::isTimerActive
        );
        ILZCD<LZCD.Dependency<STATUS>> dTownHasSupplies = prePopAble(
                "town has supplies",
                town::hasSupplies
        );
        ILZCD<LZCD.Dependency<STATUS>> dHasPlaceToUseSupplies = prePopAble(
                "town has places to use supplies",
                town::canUseMoreSupplies
        );
        LZCD<STATUS> root = new LZCD<>(
                "work without items",
                LZCD.leaf(job::tryChoosingItemlessWork, Objects::isNull),
                ImmutableList.of(
                        dPrioritizeExtraction,
                        dStatusNotGoing
                ),
                LZCD.oneDep(
                        "use items",
                        LZCD.leaf(() -> job.tryUsingSupplies(supplyItemStatus), Objects::isNull),
                        dHasWorkItems,
                        new LZCD<>(
                                "drop loot when hands full",
                                leaf(factory::droppingLoot),
                                ImmutableList.of(
                                        dInventoryFull,
                                        dTownHasSpace
                                ),
                                LZCD.oneDep(
                                        "stop when no space and hands full",
                                        leaf(factory::noSpace),
                                        dInventoryFull,
                                        new LZCD<>(
                                                "drop loot from non-full hands before starting more work",
                                                leaf(factory::droppingLoot),
                                                ImmutableList.of(
                                                        dHasNonWorkItems,
                                                        dTownHasSpace
                                                ),
                                                new LZCD<>(
                                                        "get work supplies",
                                                        leaf(factory::collectingSupplies),
                                                        ImmutableList.of(
                                                                dTownHasSupplies,
                                                                dHasPlaceToUseSupplies
                                                        ),
                                                        new LZCD<>(
                                                                "drop loot when no work supplies available",
                                                                leaf(factory::droppingLoot),
                                                                ImmutableList.of(
                                                                        dHasNonWorkItems,
                                                                        dHasPlaceToUseSupplies,
                                                                        dTownHasSpace
                                                                ),
                                                                new LZCD<>(
                                                                        "drop loot when no work possible",
                                                                        leaf(factory::droppingLoot),
                                                                        ImmutableList.of(
                                                                                dHasAnyItems,
                                                                                dTownHasSpace
                                                                        ),
                                                                        new LZCD<>(
                                                                                "wait for next stage is timer is active",
                                                                                leaf(factory::waitingForTimedState),
                                                                                ImmutableList.of(
                                                                                        dTimerActive
                                                                                ),
                                                                                new LZCD<>(
                                                                                        "stop when nowhere to work and town has items",
                                                                                        leaf(factory::noJobSite),
                                                                                        ImmutableList.of(
                                                                                                dTownHasSupplies,
                                                                                                dInventoryEmpty
                                                                                        ),
                                                                                        new LZCD<>(
                                                                                                "stop when no space and holding any items",
                                                                                                leaf(factory::noSpace),
                                                                                                ImmutableList.of(
                                                                                                        dHasAnyItems
                                                                                                ),
                                                                                                leaf(factory::noSupplies)
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
        return nullIfUnchanged(currentStatus, root.resolve());
    }

    private static <STATUS extends IStatus<STATUS>> @NotNull ILZCD<STATUS> leaf(Supplier<STATUS> factory) {
        return LZCD.leaf(factory, Objects::isNull);
    }

    /**
     * A standard daily routine for mobs who insert materials into a block in
     * their job-related room(s) and then remove a product from the room once
     * it's ready.
     *
     * @param prioritizeExtraction: If set to true, the entity will remove
     *                              finished products from blocks even if they
     *                              don't have space in their inventory. This
     *                              will cause the item to be spawned into the
     *                              world for collection by whomever walks by.
     */
    public static <STATUS extends IProductionStatus<STATUS>, ROOM extends Room> STATUS productionRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<Integer> inventory,
            EntityLocStateProvider<ROOM> entity,
            JobTownProvider<ROOM> town,
            IProductionJob<STATUS> job,
            IProductionStatusFactory<STATUS> factory
    ) {
        if (factory.waitingForTimedState().equals(currentStatus)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return null;
            }
        }
        STATUS status = usualRoutine(
                currentStatus, prioritizeExtraction, inventory,
                new TownStateProvider() {
                    @Override
                    public boolean hasSupplies() {
                        return town.hasSupplies();
                    }

                    @Override
                    public boolean hasSpace() {
                        return town.hasSpace();
                    }


                    @Override
                    public boolean isTimerActive() {
                        return town.isUnfinishedTimeWorkPresent();
                    }

                    @Override
                    public boolean canUseMoreSupplies() {
                        return !town.roomsNeedingIngredientsByState()
                                .entrySet()
                                .stream()
                                .allMatch(v -> v.getValue().isEmpty());
                    }
                },
                new Job<>() {
                    @Override
                    public @Nullable STATUS tryChoosingItemlessWork() {
                        ROOM location = entity.getEntityCurrentJobSite();
                        Collection<Integer> states = town.getStatesWithUnfinishedItemlessWork();
                        if (!states.isEmpty()) {
                            for (Integer state : states) {
                                // TODO[ASAP]: Unit test
                                if (location != null) {
                                    if (town.roomsAtState(state).contains(location)) {
                                        return factory.fromJobBlockState(state);
                                    }
                                }
                            }
                            return factory.goingToJobSite();
                        }

                        Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                        if (rooms.isEmpty()) {
                            return null;
                        }

                        if (location != null) {
                            if (rooms.contains(location)) {
                                return factory.extractingProduct();
                            }
                        }

                        return factory.goingToJobSite();
                    }

                    @Override
                    public @Nullable STATUS tryUsingSupplies(Map<Integer, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        ROOM location = entity.getEntityCurrentJobSite();
                        Map<Integer, ? extends Collection<ROOM>> roomNeedsMap = town.roomsNeedingIngredientsByState();

                        roomNeedsMap = sanitizeRoomNeeds(roomNeedsMap);

                        boolean foundWork = false;

                        List<Integer> orderedWithSupplies = job.getAllWorkStatesSortedByPreference()
                                .stream()
                                .filter(work -> supplyItemStatus.getOrDefault(work, false))
                                .toList();

                        for (Integer s : orderedWithSupplies) {
                            if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s)
                                    .isEmpty()) { // TODO: Unit test the second leg of this condition
                                foundWork = true;
                                if (location != null) {
                                    if (roomNeedsMap.get(s).contains(location)) {
                                        return factory.fromJobBlockState(s);
                                    }
                                }
                            }
                        }

                        if (foundWork) {
                            return factory.goingToJobSite();
                        }
                        // TODO: Return null here. This call to `try` might be needed for the farmer job.
                        //  Let's convert that into a production job.
                        return job.tryUsingSupplies(supplyItemStatus);
                    }
                }, factory
        );
        // TODO: For "no supplies" status, ignore rooms that only need tools
        // Because rooms needing tools "need supplies" at all times, the logic chooses that status.
        if (status == null || factory.idle().equals(status) || factory.noSupplies().equals(status)) {
            if (town.isUnfinishedTimeWorkPresent()) {
                return factory.waitingForTimedState();
            }
        }
        return status;
    }

    public static <ROOM extends Room> Map<Integer, ? extends Collection<ROOM>> sanitizeRoomNeeds(
            Map<Integer, ? extends Collection<ROOM>> roomNeedsMap
    ) {
        // If a single room needs supplies (for example) for BOTH states 0 and 1, it should only
        // show up as "needing" 0.
        Map<Integer, Collection<ROOM>> b = new HashMap<>();
        roomNeedsMap.forEach((k, rooms) -> {
            ImmutableSet.Builder<ROOM> allPrevRooms = ImmutableSet.builder();
            for (int i = 0; i < k; i++) {
                Collection<ROOM> elements = b.get(i);
                if (elements == null) {
                    elements = ImmutableList.of();
                }
                allPrevRooms.addAll(elements);
            }
            ImmutableSet<ROOM> prevRooms = allPrevRooms.build();
            ImmutableList.Builder<ROOM> bld = ImmutableList.builder();
            rooms.forEach(room -> {
                if (prevRooms.contains(room)) {
                    return;
                }
                bld.add(room);
            });
            ImmutableList<ROOM> build = bld.build();
            if (!build.isEmpty()) {
                b.put(k, build);
            }
        });
        return ImmutableMap.copyOf(b);
    }


    private static <S> S nullIfUnchanged(
            S oldStatus,
            S newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }

    private static <STATUS> LZCD<LZCD.Dependency<STATUS>> prePopAble(
            String name, Supplier<Boolean> s
    ) {
        return LZCD.noDeps(
                name,
                () -> new LZCD.Dependency<STATUS>() {
                    private WithReason<Boolean> value;

                    @Override
                    public LZCD.Populated<WithReason<Boolean>> populate() {
                        // TODO: Pass dependencies as inputs to usualRoutine
                        this.value = WithReason.always(s.get(), "input");
                        return new LZCD.Populated<>(
                                name,
                                value,
                                ImmutableMap.of(),
                                null
                        );
                    }

                    @Override
                    public String describe() {
                        String v = value == null ? "<?>" : value.toString();
                        return name + '=' + v;
                    }

                    @Override
                    public WithReason<Boolean> apply(Supplier<STATUS> statusSupplier) {
                        populate();
                        return this.value;
                    }
                }, v -> false
        );
    }

    private static <STATUS> LZCD<LZCD.Dependency<STATUS>> input(
            String name, Function<STATUS, Boolean> s
    ) {
        return LZCD.noDeps(
                name,
                () -> new LZCD.Dependency<>() {
                    @Override
                    public LZCD.Populated<WithReason<Boolean>> populate() {
                        // Cannot be pre-populated
                        return new LZCD.Populated<>(
                                name,
                                WithReason.always(null, "cannot be pre-computed"),
                                ImmutableMap.of(),
                                null
                        );
                        // TODO: Pass dependencies as inputs to usualRoutine
                    }

                    @Override
                    public String describe() {
                        return name + "=<?>";
                    }

                    @Override
                    public WithReason<Boolean> apply(Supplier<STATUS> statusSupplier) {
                        return WithReason.always(s.apply(statusSupplier.get()), "input");
                    }
                }, v -> false
        );
    }
}
