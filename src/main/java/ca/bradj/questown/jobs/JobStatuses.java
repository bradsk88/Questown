package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionJob;
import ca.bradj.questown.jobs.production.IProductionStatus;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobStatuses {

    public static boolean hasItems(EntityInvStateProvider<?> inventory) {
        if (inventory.hasNonSupplyItems()) {
            return true;
        }
        return inventory.getSupplyItemStatus().values().stream().anyMatch(Boolean::booleanValue);
    }

    public interface Job<STATUS, SUP_CAT> {
        @Nullable STATUS tryChoosingItemlessWork();

        @Nullable STATUS tryUsingSupplies(Map<SUP_CAT, Boolean> supplyItemStatus);
    }

    public static <STATUS extends IStatus<STATUS>, SUP_CAT> STATUS usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<SUP_CAT> inventory,
            TownStateProvider town,
            Job<STATUS, SUP_CAT> job,
            IStatusFactory<STATUS> factory
    ) {
        STATUS s = null;
        Map<SUP_CAT, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();
        boolean hasWorkItems = supplyItemStatus.containsValue(true);
        boolean hasItems = hasWorkItems || inventory.hasNonSupplyItems();
        if (hasWorkItems) {
            STATUS useStatus = job.tryUsingSupplies(supplyItemStatus);
            if (useStatus != null) {
                s = useStatus;
                // TODO[Testing]: This "else" breaks jobs that use tools at multiple stages
//            } else {
//                hasWorkItems = false;
            }
        }
        if (s == null && inventory.inventoryFull()) {
            if (town.hasSpace()) {
                s = factory.droppingLoot();
            } else {
                s = factory.noSpace();
            }
        }

        @Nullable STATUS s2 = job.tryChoosingItemlessWork();
        if (s2 != null && prioritizeExtraction && s2 != factory.goingToJobSite()) {
            return s2;
        }

        if (s != null) {
            s = nullIfUnchanged(currentStatus, s);
            if (s != factory.goingToJobSite()) {
                return s;
            } else if (inventory.inventoryFull() || (hasItems && !town.hasSupplies())) {
                return s;
            }
        }

        if (s2 != null) {
            return nullIfUnchanged(currentStatus, s2);
        } else if (inventory.hasNonSupplyItems()) {
            s2 = factory.droppingLoot();
        } else if (!town.hasSupplies()) {
            if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.noSupplies());
            } else if (hasItems) {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            } else {
                s2 = factory.idle();
            }
        } else {
            if (hasItems && !hasWorkItems) {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            } else if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.collectingSupplies());
            } else {
                return factory.idle();
            }
        }

        if (s2 != factory.collectingSupplies() && s != null) {
            return s;
        }

        return s2;
    }

    /**
     * A standard daily routine for mobs who insert materials into a block in
     * their job-related room(s) and then remove a product from the room once
     * it's ready.
     * @param prioritizeExtraction: If set to true, the entity will remove
     *                            finished products from blocks even if they
     *                            don't have space in their inventory. This
     *                            will cause the item to be spawned into the
     *                            world for collection by whomever walks by.
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
                            public boolean canUseMoreSupplies() {
                                return !town.roomsNeedingIngredientsByState()
                                        .entrySet()
                                        .stream()
                                        .allMatch(v -> v.getValue().isEmpty());
                            }

                            @Override
                            public boolean isUnfinishedTimeWorkPresent() {
                                return town.isUnfinishedTimeWorkPresent();
                            }
                        },
                        new Job<>() {
                            @Override
                            public @Nullable STATUS tryChoosingItemlessWork() {
                                Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                                if (rooms.isEmpty()) {
                                    return null;
                                }

                                ROOM location = entity.getEntityCurrentJobSite();
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
}

