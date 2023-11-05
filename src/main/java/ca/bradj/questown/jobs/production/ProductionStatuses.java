package ca.bradj.questown.jobs.production;

import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class ProductionStatuses {

    public static @Nullable <ROOM extends Room> ProductionStatus getNewStatusFromSignal(
            ProductionStatus currentStatus,
            Signals signal,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory
    ) {
        switch (signal) {
            case MORNING, NOON -> {
                return getMorningStatus(currentStatus, inventory, town, entity, factory);
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town, factory);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getMorningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory
    ) {
        ProductionStatus newStatus = JobStatuses.productionRoutine(
                currentStatus, true, inventory, entity, town,
                // TODO: Allow preferences to be provided/serialized
                new TypicalProductionJob<>(ImmutableList.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                factory
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getEveningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            IStatusFactory<ProductionStatus> factory
    ) {
        if (JobStatuses.hasItems(inventory)) {
            return nullIfUnchanged(currentStatus, factory.droppingLoot());
        }

        return nullIfUnchanged(currentStatus, factory.relaxing());
    }

    private static ProductionStatus nullIfUnchanged(
            ProductionStatus oldStatus,
            ProductionStatus newStatus
    ) {
        if (oldStatus.equals(newStatus)) {
            return null;
        }
        return newStatus;
    }
}
