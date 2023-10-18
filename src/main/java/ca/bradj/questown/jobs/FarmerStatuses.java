package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FarmerStatuses {

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            TownStateProvider town,
            FarmStateProvider farm,
            EntityStateProvider entity,
            Signals signal,
            boolean isInFarm
    ) {
//        if (!inventory.isValid()) {
//            throw new IllegalStateException("Inventory state is invalid");
//        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING, NOON, EVENING -> {
                return handleMorning(currentStatus, town, farm, entity, isInFarm);
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

    public interface FarmStateProvider {
        boolean isWorkPossible(FarmerJob.FarmerAction action);
    }

    public static GathererJournal.@Nullable Status handleMorning(
            GathererJournal.Status currentStatus,
            TownStateProvider town,
            FarmStateProvider farm,
            EntityStateProvider inventory,
            boolean isInFarm
    ) {
        GathererJournal.Status status = JobStatuses.usualRoutine(
                currentStatus, inventory, town, false,
                new JobStatuses.Job() {
                    @Override
                    public GathererJournal.@Nullable Status tryDoingItemlessWork() {
                        // Order is important here
                        if (farm.isWorkPossible(FarmerJob.FarmerAction.HARVEST)) {
                            return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_HARVESTING, isInFarm);
                        }
                        if (farm.isWorkPossible(FarmerJob.FarmerAction.TILL)) {
                            return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_TILLING, isInFarm);
                        }
                        return null;
                    }

                    @Override
                    public @Nullable GathererJournal.Status tryUsingSupplies(
                            Map<GathererJournal.Status, Boolean> supplyItemStatus
                    ) {
                        // Order of work type is important here
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.FARMING_BONING, false)) {
                            if (farm.isWorkPossible(FarmerJob.FarmerAction.BONE)) {
                                return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_BONING, isInFarm);
                            }
                        }
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.FARMING_PLANTING, false)) {
                            if (farm.isWorkPossible(FarmerJob.FarmerAction.PLANT)) {
                                return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_PLANTING, isInFarm);
                            }
                        }
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.FARMING_TILLING, false)) {
                            if (farm.isWorkPossible(FarmerJob.FarmerAction.TILL)) {
                                return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_TILLING, isInFarm);
                            }
                        }
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.FARMING_COMPOSTING, false)) {
                            if (farm.isWorkPossible(FarmerJob.FarmerAction.COMPOST)) {
                                return JobsClean.doOrGoTo(GathererJournal.Status.FARMING_COMPOSTING, isInFarm);
                            }
                        }
                        return null;
                    }
                }
        );
        if (status == GathererJournal.Status.DROPPING_LOOT && isInFarm) {
            return nullIfUnchanged(currentStatus, GathererJournal.Status.LEAVING_FARM);
        }
        return status;
    }

    private static GathererJournal.@Nullable Status handleNoon(
            GathererJournal.Status currentStatus,
            boolean isInFarm
    ) {
        if (isInFarm) {
            if (currentStatus == GathererJournal.Status.FARMING_RANDOM_TEND) {
                return null;
            }
            return GathererJournal.Status.FARMING_RANDOM_TEND;
        }
        if (currentStatus == GathererJournal.Status.GOING_TO_JOBSITE) {
            return null;
        }
        return GathererJournal.Status.GOING_TO_JOBSITE;
    }

    private static GathererJournal.@Nullable Status handleEvening(
            GathererJournal.Status currentStatus,
            boolean isInFarm
    ) {
        if (isInFarm) {
            if (currentStatus == GathererJournal.Status.FARMING_RANDOM_TEND) {
                return null;
            }
            return GathererJournal.Status.FARMING_RANDOM_TEND;
        }
        if (currentStatus == GathererJournal.Status.GOING_TO_JOBSITE) {
            return null;
        }
        return GathererJournal.Status.GOING_TO_JOBSITE;
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
