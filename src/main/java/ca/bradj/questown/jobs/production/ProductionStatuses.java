package ca.bradj.questown.jobs.production;

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
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction
    ) {
        switch (signal) {
            case MORNING, NOON -> {
                return getMorningStatus(currentStatus, inventory, town, entity, factory, prioritizeExtraction);
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town, factory, factory);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> @Nullable ProductionStatus getMorningStatus(
            ProductionStatus currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction
    ) {
        ProductionStatus newStatus = JobStatuses.productionRoutine(
                currentStatus, prioritizeExtraction, inventory, entity, town,
                // TODO: Allow preferences to be provided/serialized
                new TypicalProductionJob<>(ImmutableList.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                factory
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    /**
     * The evening/night branch, generic over the status type so it can be
     * unit-tested with a lightweight fake status. {@code waitingFactory} is
     * the source of {@code waitingForTimedState()} (an {@link
     * IProductionStatusFactory} when the concrete type supports it); {@code
     * factory} supplies the recall statuses (droppingLoot / relaxing).
     */
    public static <STATUS extends IProductionStatus<STATUS>, ROOM extends Room> @Nullable STATUS getEveningStatus(
            STATUS currentStatus,
            EntityInvStateProvider<Integer> inventory,
            JobTownProvider<ROOM> town,
            IProductionStatusFactory<STATUS> waitingFactory,
            IStatusFactory<STATUS> factory
    ) {
        // Mirror the morning path (JobStatuses.productionRoutine): a villager
        // waiting on a timed work state whose timer is still running must not
        // be recalled to town. Without this, evening/night yanks a town-leaver
        // home mid-trip (empty-handed), cutting the trip short. Returning null
        // leaves the status unchanged, so he stays out until the timer ends.
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
