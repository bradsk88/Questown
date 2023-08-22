package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class Statuses {

    public interface TownStateProvider {

        boolean IsStorageAvailable();
        boolean hasGate();
    }

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            GathererJournal.Signals signal,
            InventoryStateProvider<?> inventory,
            TownStateProvider town
    ) {
        if (!inventory.isValid()) {
            throw new IllegalStateException("Inventory state is invalid");
        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING -> {
                return handleMorning(currentStatus, inventory, town);
            }
            case NOON -> {
                return handleNoon(currentStatus, inventory, town);
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

    @Nullable
    private static GathererJournal.Status handleMorning(
            GathererJournal.Status currentStatus,
            InventoryStateProvider<?> inventory,
            TownStateProvider town
    ) {
        if (currentStatus == GathererJournal.Status.GATHERING) {
            return null;
        }

        if (inventory.hasAnyDroppableLoot()) {
            if (currentStatus == GathererJournal.Status.RETURNED_SUCCESS) {
                return GathererJournal.Status.DROPPING_LOOT;
            }
            if (!town.IsStorageAvailable()) {
                if (currentStatus == GathererJournal.Status.NO_SPACE) {
                    return null;
                }
                return GathererJournal.Status.NO_SPACE;
            }
            if (currentStatus == GathererJournal.Status.DROPPING_LOOT) {
                return null;
            }
            return GathererJournal.Status.RETURNED_SUCCESS;
        }


        if (inventory.inventoryIsFull()) {
            return GathererJournal.Status.NO_SPACE;
        }
        if (inventory.inventoryHasFood()) {
            if (!town.hasGate()) {
                if (currentStatus != GathererJournal.Status.NO_GATE) {
                    return GathererJournal.Status.NO_GATE;
                }
                return null;
            }
            return GathererJournal.Status.GATHERING;
        }
        if (currentStatus != GathererJournal.Status.NO_FOOD) {
            return GathererJournal.Status.NO_FOOD;
        }
        return null;
    }

    private static GathererJournal.@Nullable Status handleNoon(
            GathererJournal.Status currentStatus,
            InventoryStateProvider<?> inventory,
            TownStateProvider town
    ) {
        if (ImmutableList.of(
                GathererJournal.Status.STAYING,
                GathererJournal.Status.RETURNING,
                GathererJournal.Status.IDLE
        ).contains(currentStatus)) {
            return null;
        }
        if (ImmutableList.of(
                GathererJournal.Status.RETURNING_AT_NIGHT,
                GathererJournal.Status.RETURNED_SUCCESS,
                GathererJournal.Status.RETURNED_FAILURE,
                GathererJournal.Status.RELAXING
        ).contains(currentStatus)) {
            return GathererJournal.Status.RETURNING;
        }
        if (
                currentStatus == GathererJournal.Status.NO_FOOD ||
                currentStatus == GathererJournal.Status.NO_SPACE ||
                        currentStatus == GathererJournal.Status.NO_GATE
        ) {
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

        if (!town.hasGate()) {
            return GathererJournal.Status.STAYING;
        }

        // TODO: What if the gatherer is out but doesn't have food (somehow)
        //  Maybe they return unsuccessfully (and early?)
        throw new IllegalStateException("Unhandled status branch");
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

        boolean townStorageAvailable = town.IsStorageAvailable();
        boolean hasDroppableLoot = inventory.hasAnyDroppableLoot();
        if (hasDroppableLoot && townStorageAvailable) {
            if (currentStatus != GathererJournal.Status.DROPPING_LOOT) {
                return GathererJournal.Status.DROPPING_LOOT;
            }
        }

        if (ImmutableList.of(
                GathererJournal.Status.RETURNED_SUCCESS,
                GathererJournal.Status.DROPPING_LOOT,
                GathererJournal.Status.IDLE
        ).contains(currentStatus) && !hasDroppableLoot) {
            return GathererJournal.Status.RELAXING;
        }

        if (!townStorageAvailable && hasDroppableLoot) {
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
        if (currentStatus == GathererJournal.Status.DROPPING_LOOT) {
            return null;
        }
        // TODO: Randomly fail gathering
        return GathererJournal.Status.RETURNED_SUCCESS;
    }
}
