package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

public class ProductionStatuses {

    public static @Nullable <ROOM extends Room> ProductionStatus getNewStatusFromSignal(
            ProductionStatus currentStatus,
            Signals signal,
            DEntityInvStateProvider inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IStatusFactory<ProductionStatus> factory
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
            DEntityInvStateProvider inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IStatusFactory<ProductionStatus> factory
    ) {
        ProductionStatus newStatus = JobStatuses.productionRoutine(
                currentStatus, true, inventory, entity, town,
                new TypicalProductionJob<>(ImmutableList.of(
                        ProductionStatus.EXTRACTING_PRODUCT,
                        ProductionStatus.WORKING_ON_PRODUCTION,
                        ProductionStatus.INSERTING_INGREDIENTS
                )),
                factory
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getEveningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<ProductionStatus> inventory,
            JobTownProvider<ROOM> town,
            IStatusFactory<ProductionStatus> factory
    ) {
        if (JobStatuses.hasItems(inventory)) {
            return nullIfUnchanged(currentStatus, ProductionStatus.DROPPING_LOOT);
        }

        return nullIfUnchanged(currentStatus, factory.relaxing());
    }

    private static ProductionStatus nullIfUnchanged(
            ProductionStatus oldStatus,
            ProductionStatus newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}
