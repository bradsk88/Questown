package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public class FarmerStatuses {

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            Signals signal
    ) {
//        if (!inventory.isValid()) {
//            throw new IllegalStateException("Inventory state is invalid");
//        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING -> {
                return handleMorning(currentStatus);
            }
            case NOON -> {
                return handleNoon(currentStatus);
            }
            case EVENING -> {
                return handleEvening(currentStatus);
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
            GathererJournal.Status currentStatus
    ) {
        if (currentStatus == GathererJournal.Status.FARMING) {
            return null;
        }
        return GathererJournal.Status.FARMING;
    }

    private static GathererJournal.@Nullable Status handleNoon(
            GathererJournal.Status currentStatus
    ) {
        if (currentStatus == GathererJournal.Status.FARMING) {
            return null;
        }
        return GathererJournal.Status.FARMING;
    }

    @Nullable
    private static GathererJournal.Status handleEvening(
            GathererJournal.Status currentStatus
    ) {
        if (currentStatus == GathererJournal.Status.FARMING) {
            return null;
        }
        return GathererJournal.Status.FARMING;
    }
}
