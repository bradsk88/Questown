package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class SmelterStatuses {

    public static @Nullable <ROOM extends Room> SmelterStatus getNewStatusFromSignal(
            SmelterStatus currentStatus,
            Signals signal,
            EntityInvStateProvider<SmelterStatus> inventory,
            JobTownProvider<SmelterStatus, ROOM> town,
            EntityLocStateProvider<ROOM> entity
    ) {
        switch (signal) {
            case MORNING, NOON -> {
                return getMorningStatus(currentStatus, inventory, town, entity);
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> @Nullable SmelterStatus getMorningStatus(
            SmelterStatus currentStatus,
            EntityInvStateProvider<SmelterStatus> inventory,
            JobTownProvider<SmelterStatus, ROOM> town,
            EntityLocStateProvider<ROOM> entity
    ) {
        SmelterStatus newStatus = JobStatuses.productionRoutine(
                currentStatus, true, inventory, entity, town,
                new TypicalProductionJob<>(ImmutableList.of(
                        SmelterStatus.WORK_COLLECTING_RAW_PRODUCT,
                        SmelterStatus.WORK_PROCESSING_ORE,
                        SmelterStatus.WORK_INSERTING_ORE
                )),
                SmelterStatus.FACTORY
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    public static <ROOM extends Room> @Nullable SmelterStatus getEveningStatus(
            SmelterStatus currentStatus,
            EntityInvStateProvider<SmelterStatus> inventory,
            JobTownProvider<SmelterStatus, ROOM> town
    ) {
        if (JobStatuses.hasItems(inventory)) {
            return nullIfUnchanged(currentStatus, SmelterStatus.DROPPING_LOOT);
        }

        return nullIfUnchanged(currentStatus, SmelterStatus.RELAXING);
    }

    private static SmelterStatus nullIfUnchanged(
            SmelterStatus oldStatus,
            SmelterStatus newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}
