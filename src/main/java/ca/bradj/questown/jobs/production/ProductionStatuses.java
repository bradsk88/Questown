package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public class ProductionStatuses {

    public static @Nullable <ROOM extends Room> ProductionStatus getNewStatusFromSignal(
            ProductionStatus currentStatus,
            Signals signal,
            EntityInvStateProvider<ProductionStatus> inventory,
            JobTownProvider<ROOM, ProductionStatus> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction,
            ImmutableList<ProductionStatus> statePriority
    ) {
        switch (signal) {
            case MORNING, NOON -> {
                return getMorningStatus(
                        currentStatus,
                        inventory,
                        town,
                        entity,
                        factory,
                        prioritizeExtraction,
                        statePriority
                );
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town, factory, town.isCachingAllowed());
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getMorningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<ProductionStatus> inventory,
            JobTownProvider<ROOM, ProductionStatus> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction,
            ImmutableList<ProductionStatus> statePriority
    ) {
        Comparator<ProductionStatus> cmp = Comparator.comparingInt(ProductionStatus::getProductionState);
        ProductionStatus maxState = statePriority.stream().max(cmp).orElse(currentStatus);
        WithReason<ProductionStatus> newStatus = JobStatuses.productionRoutine(
                currentStatus, prioritizeExtraction, inventory, entity, town,
                new TypicalProductionJob<>(statePriority),
                factory, maxState
        );
        return nullIfUnchanged(currentStatus, WithReason.orNull(newStatus));
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getEveningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<ProductionStatus> inventory,
            JobTownProvider<ROOM, ProductionStatus> town,
            IStatusFactory<ProductionStatus> factory,
            boolean allowCaching
    ) {
        if (JobStatuses.hasItems(allowCaching, inventory)) {
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
