package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class Statuses {

    public interface TownStateProvider {

        boolean IsStorageAvailable();
    }

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            GathererJournal.Signals signal,
            InventoryStateProvider<?> inventory,
            TownStateProvider town
    ) {
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING -> {
                if (currentStatus == GathererJournal.Status.GATHERING) {
                    return null;
                }

                if (inventory.hasAnyLoot()) {
                    if (currentStatus != GathererJournal.Status.NO_SPACE && !town.IsStorageAvailable()) {
                        return GathererJournal.Status.NO_SPACE;
                    }
                    if (currentStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                        return null;
                    }
                    return GathererJournal.Status.RETURNED_SUCCESS;
                }


                if (inventory.inventoryIsFull()) {
                    return GathererJournal.Status.NO_SPACE;
                }
                if (inventory.inventoryHasFood()) {
                    return GathererJournal.Status.GATHERING;
                }
                if (currentStatus != GathererJournal.Status.NO_FOOD) {
                    return GathererJournal.Status.NO_FOOD;
                }
                return null;
            }
            case NOON -> {
                return handleNoon(currentStatus, inventory);
            }
            case EVENING -> {
                return handleEvening(currentStatus, inventory, town);
            }
            case NIGHT -> {
                if (currentStatus == GathererJournal.Status.STAYING || currentStatus == GathererJournal.Status.RETURNED_FAILURE || currentStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                    return null;
                }
                // TODO: Late return?
                // TODO: Gatherers can get captured and must be rescued by knight?
                return null;
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    private static GathererJournal.@Nullable Status handleNoon(
            GathererJournal.Status currentStatus,
            InventoryStateProvider<?> inventory
    ) {
        if (currentStatus == GathererJournal.Status.STAYING || currentStatus == GathererJournal.Status.RETURNING) {
            return null;
        }
        if (ImmutableList.of(
                GathererJournal.Status.RETURNING_AT_NIGHT,
                GathererJournal.Status.RETURNED_SUCCESS,
                GathererJournal.Status.RETURNED_FAILURE,
                GathererJournal.Status.RELAXING
        ).contains(currentStatus)) {
            return GathererJournal.Status.NO_FOOD;
        }
        if (currentStatus == GathererJournal.Status.NO_FOOD || currentStatus == GathererJournal.Status.NO_SPACE) {
            return GathererJournal.Status.STAYING;
        }
        if (currentStatus == GathererJournal.Status.GATHERING) {
            return GathererJournal.Status.GATHERING_HUNGRY;
        }
        if (currentStatus == GathererJournal.Status.GATHERING_HUNGRY) {
            if (inventory.inventoryHasFood()) {
                return GathererJournal.Status.GATHERING_EATING;
            }
            return GathererJournal.Status.NO_FOOD;
        }
        if (currentStatus == GathererJournal.Status.GATHERING_EATING) {
            // Assume the entity or time warp logic handled the consumption of the food item
            return GathererJournal.Status.RETURNING;
        }

        // TODO: What if the gatherer is out but doesn't have food (somehow)
        //  Maybe they return unsuccessfully (and early?)
        return null;
    }

    @Nullable
    private static GathererJournal.Status handleEvening(
            GathererJournal.Status currentStatus,
            InventoryStateProvider<?> inventory,
            TownStateProvider town
    ) {
        if (currentStatus == GathererJournal.Status.STAYING) {
            return null;
        }
        if (currentStatus == GathererJournal.Status.NO_FOOD) {
            return GathererJournal.Status.STAYING;
        }

        if (ImmutableList.of(
                GathererJournal.Status.RETURNED_SUCCESS,
                GathererJournal.Status.IDLE
        ).contains(currentStatus) && !inventory.hasAnyLoot()) {
            return GathererJournal.Status.RELAXING;
        }

        if (!town.IsStorageAvailable() && inventory.hasAnyLoot()) {
            if (currentStatus != GathererJournal.Status.NO_SPACE) {
                return GathererJournal.Status.NO_SPACE;
            }
            return null;
        }

        if (currentStatus == GathererJournal.Status.RETURNED_FAILURE || currentStatus == GathererJournal.Status.RETURNED_SUCCESS || currentStatus == GathererJournal.Status.RELAXING) {
            return null;
        }

        if (currentStatus == GathererJournal.Status.GATHERING) {
            return GathererJournal.Status.GATHERING_HUNGRY;
        }
        if (currentStatus == GathererJournal.Status.GATHERING_HUNGRY) {
            if (inventory.inventoryHasFood()) {
                return GathererJournal.Status.GATHERING_EATING;
            }
            return GathererJournal.Status.NO_FOOD;
        }
        if (currentStatus == GathererJournal.Status.GATHERING_EATING) {
            // Assume the entity or time warp logic handled the consumption of the food item
            return GathererJournal.Status.RETURNING_AT_NIGHT;
        }
        // TODO: Randomly fail gathering
        return GathererJournal.Status.RETURNED_SUCCESS;
    }
}
