package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JobStatuses {

    public interface Job {
        @Nullable GathererJournal.Status tryDoingSpecializedWork();
        @Nullable GathererJournal.Status tryUsingSupplies(Map<GathererJournal.Status, Boolean> supplyItemStatus);
    }

    public static GathererJournal.Status usualRoutine(
            GathererJournal.Status currentStatus,
            EntityStateProvider inventory,
            TownStateProvider town,
            Job job
    ) {
        if (inventory.hasItems()) {
            GathererJournal.Status useStatus = job.tryUsingSupplies(inventory.getSupplyItemStatus());
            if (useStatus != null) {
                return nullIfUnchanged(currentStatus, useStatus);
            }
            if (inventory.inventoryFull()) {
                return nullIfUnchanged(currentStatus, GathererJournal.Status.DROPPING_LOOT);
            }
        }

        @Nullable GathererJournal.Status workStatus = job.tryDoingSpecializedWork();
        if (workStatus != null) {
            return nullIfUnchanged(currentStatus, workStatus);
        }

        if (inventory.hasNonSupplyItems()) {
            return GathererJournal.Status.DROPPING_LOOT;
        }

        if (!town.hasSupplies()) {
            return nullIfUnchanged(currentStatus, GathererJournal.Status.NO_SUPPLIES);
        }

        return nullIfUnchanged(currentStatus, GathererJournal.Status.COLLECTING_SUPPLIES);
    }


    private static GathererJournal.Status nullIfUnchanged(
            GathererJournal.Status oldStatus,
            GathererJournal.Status newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}
