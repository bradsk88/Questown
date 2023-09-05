package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.space.Position;
import org.jetbrains.annotations.Nullable;

public class FarmerStatuses {

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            Signals signal,
            boolean isInFarm
    ) {
//        if (!inventory.isValid()) {
//            throw new IllegalStateException("Inventory state is invalid");
//        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING -> {
                return handleMorning(currentStatus, isInFarm);
            }
            case NOON -> {
                return handleNoon(currentStatus, isInFarm);
            }
            case EVENING -> {
                return handleEvening(currentStatus, isInFarm);
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

    private static GathererJournal.@Nullable Status handleMorning(
            GathererJournal.Status currentStatus,
            boolean isInFarm
    ) {
        if (isInFarm) {
            if (currentStatus == GathererJournal.Status.FARMING) {
                return null;
            }
            return GathererJournal.Status.FARMING;
        }
        if (currentStatus == GathererJournal.Status.WALKING_TO_FARM) {
            return null;
        }
        return GathererJournal.Status.WALKING_TO_FARM;
    }

    private static GathererJournal.@Nullable Status handleNoon(
            GathererJournal.Status currentStatus,
            boolean isInFarm
    ) {
        if (isInFarm) {
            if (currentStatus == GathererJournal.Status.FARMING) {
                return null;
            }
            return GathererJournal.Status.FARMING;
        }
        if (currentStatus == GathererJournal.Status.WALKING_TO_FARM) {
            return null;
        }
        return GathererJournal.Status.WALKING_TO_FARM;
    }

    private static GathererJournal.@Nullable Status handleEvening(
            GathererJournal.Status currentStatus,
            boolean isInFarm
    ) {
        if (isInFarm) {
            if (currentStatus == GathererJournal.Status.FARMING) {
                return null;
            }
            return GathererJournal.Status.FARMING;
        }
        if (currentStatus == GathererJournal.Status.WALKING_TO_FARM) {
            return null;
        }
        return GathererJournal.Status.WALKING_TO_FARM;
    }
}
