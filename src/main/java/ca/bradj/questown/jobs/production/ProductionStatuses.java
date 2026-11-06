package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class ProductionStatuses {

    public static @Nullable <ROOM extends Room> ProductionStatus getNewStatusFromSignal(
            JobID jobId,
            ProductionStatus currentStatus,
            Signals signal,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction
    ) {
        switch (signal) {
            case MORNING, NOON -> {
                return getMorningStatus(jobId, currentStatus, inventory, town, entity, factory, prioritizeExtraction);
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town, factory, factory);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getMorningStatus(
            JobID jobId,
            ProductionStatus currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction
    ) {
        ProductionStatus newStatus = JobStatuses.productionRoutine(
                jobId, currentStatus, prioritizeExtraction, inventory, entity, town,
                // TODO: Allow preferences to be provided/serialized
                new TypicalProductionJob<>(ImmutableList.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                factory
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    /** The evening and night statuses. */
    public static <STATUS extends IProductionStatus<STATUS>, ROOM extends Room> @Nullable STATUS getEveningStatus(
            STATUS currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            IProductionStatusFactory<STATUS> waitingFactory,
            IStatusFactory<STATUS> factory
    ) {
        // A villager on a timed trip stays out until its timer ends.
        if (waitingFactory.waitingForTimedState().equals(currentStatus) && town.isUnfinishedTimeWorkPresent()) {
            return null;
        }
        if (JobStatuses.hasItems(inventory)) {
            return nullIfUnchanged(currentStatus, factory.droppingLoot());
        }

        return nullIfUnchanged(currentStatus, factory.relaxing());
    }

    private static <STATUS extends IStatus<STATUS>> STATUS nullIfUnchanged(
            STATUS oldStatus,
            STATUS newStatus
    ) {
        if (oldStatus.equals(newStatus)) {
            return null;
        }
        return newStatus;
    }
}
