package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JobStatuses {

    public static boolean hasItems(EntityInvStateProvider<?> inventory) {
        if (inventory.hasNonSupplyItems()) {
            return true;
        }
        return inventory.getSupplyItemStatus().values().stream().anyMatch(Boolean::booleanValue);
    }

    public interface Job<STATUS> {
        @Nullable STATUS tryChoosingItemlessWork();

        @Nullable STATUS tryUsingSupplies(Map<STATUS, Boolean> supplyItemStatus);
    }

    public static <STATUS extends IStatus<STATUS>> STATUS usualRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<STATUS> inventory,
            TownStateProvider town,
            Job<STATUS> job,
            IStatusFactory<STATUS> factory
    ) {
        STATUS s = null;
        Map<STATUS, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();
        boolean hasWorkItems = supplyItemStatus.containsValue(true);
        boolean hasItems = hasWorkItems || inventory.hasNonSupplyItems();
        if (hasWorkItems) {
            STATUS useStatus = job.tryUsingSupplies(supplyItemStatus);
            if (useStatus != null) {
                s = useStatus;
            } else {
                hasWorkItems = false;
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
    public static <STATUS extends IStatus<STATUS>, ROOM extends Room> STATUS productionRoutine(
            STATUS currentStatus,
            boolean prioritizeExtraction,
            EntityInvStateProvider<STATUS> inventory,
            EntityLocStateProvider<ROOM> entity,
            JobTownProvider<STATUS, ROOM> town,
            IProductionJob<STATUS> job,
            IStatusFactory<STATUS> factory
    ) {
        return usualRoutine(
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
                        return !town.roomsNeedingIngredients()
                                .entrySet()
                                .stream()
                                .allMatch(v -> v.getValue().isEmpty());
                    }
                },
                new Job<>() {
                    @Override
                    public @Nullable STATUS tryChoosingItemlessWork() {
                        Collection<ROOM> rooms = town.roomsWithCompletedProduct();
                        if (rooms.isEmpty()) {
                            return null;
                        }

                        return factory.collectingFinishedProduct();
                    }

                    @Override
                    public @Nullable STATUS tryUsingSupplies(Map<STATUS, Boolean> supplyItemStatus) {
                        if (supplyItemStatus.isEmpty()) {
                            return null;
                        }
                        RoomRecipeMatch<ROOM> location = entity.getEntityCurrentJobSite();
                        Map<STATUS, ? extends Collection<ROOM>> roomNeedsMap = town.roomsNeedingIngredients();

                        boolean foundWork = false;

                        List<STATUS> orderedWithSupplies = job.getAllWorkStatusesSortedByPreference()
                                .stream()
                                .filter(work -> supplyItemStatus.getOrDefault(work, false))
                                .toList();

                        for (STATUS s : orderedWithSupplies) {
                            if (roomNeedsMap.containsKey(s) && !roomNeedsMap.get(s).isEmpty()) { // TODO: Unit test the second leg of this condition
                                foundWork = true;
                                if (location != null) {
                                    if (roomNeedsMap.get(s).contains(location.room)) {
                                        return s;
                                    }
                                }
                            }
                        }

                        if (foundWork) {
                            return factory.goingToJobSite();
                        }
                        return job.tryUsingSupplies(supplyItemStatus);
                    }
                }, factory
        );
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

