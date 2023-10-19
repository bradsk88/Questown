package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JobStatuses {

    public interface Job {
        @Nullable GathererJournal.Status tryChoosingItemlessWork();

        @Nullable GathererJournal.Status tryUsingSupplies(Map<GathererJournal.Status, Boolean> supplyItemStatus);
    }

    public static GathererJournal.Status usualRoutine(
            GathererJournal.Status currentStatus,
            EntityStateProvider inventory,
            TownStateProvider town,
            boolean fillBeforeWorking,
            Job job
    ) {
        GathererJournal.Status s = null;
        if (inventory.hasItems()) {
            GathererJournal.Status useStatus = job.tryUsingSupplies(inventory.getSupplyItemStatus());
            if (useStatus != null) {
                s = useStatus;
            } else if (inventory.inventoryFull()) {
                if (town.hasSpace()) {
                    s = GathererJournal.Status.DROPPING_LOOT;
                } else {
                    s = GathererJournal.Status.NO_SPACE;
                }
            }
        }

        if (s != null) {
            s = nullIfUnchanged(currentStatus, s);
            if (s != GathererJournal.Status.GOING_TO_JOBSITE) {
                return s;
            } else if (inventory.inventoryFull() || (inventory.hasItems() && !town.hasSupplies())) {
                return s;
            }
        }

        @Nullable GathererJournal.Status s2 = job.tryChoosingItemlessWork();
        if (s2 != null) {
            return nullIfUnchanged(currentStatus, s2);
        } else if (inventory.hasNonSupplyItems()) {
            s2 = GathererJournal.Status.DROPPING_LOOT;
        } else if (!town.hasSupplies()) {
            s2 = nullIfUnchanged(currentStatus, GathererJournal.Status.NO_SUPPLIES);
        } else {
            if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, GathererJournal.Status.COLLECTING_SUPPLIES);
            } else {
                return GathererJournal.Status.IDLE;
            }
        }

        if (s2 != GathererJournal.Status.COLLECTING_SUPPLIES && s != null) {
            return s;
        }

        return s2;
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
