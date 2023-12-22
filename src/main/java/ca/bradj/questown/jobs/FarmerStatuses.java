package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FarmerStatuses {

    public static @Nullable GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            TownProvider town,
            FarmStateProvider farm,
            EntityInvStateProvider entity,
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

    private static final ImmutableMap<FarmerJob.FarmerAction, GathererJournal.Status> ITEM_WORK = ImmutableMap.of(
            // Order here implies preference - which is important.
            FarmerJob.FarmerAction.BONE, GathererJournal.Status.FARMING_BONING,
            FarmerJob.FarmerAction.PLANT, GathererJournal.Status.FARMING_PLANTING,
            FarmerJob.FarmerAction.TILL, GathererJournal.Status.FARMING_TILLING,
            FarmerJob.FarmerAction.COMPOST, GathererJournal.Status.FARMING_COMPOSTING
    );

    // Itemless work is done before collecting supplies
    private static final ImmutableMap<FarmerJob.FarmerAction, GathererJournal.Status> PRIORITY_ITEMLESS_WORK = ImmutableMap.of(
            // Order here implies preference - which is important.
            FarmerJob.FarmerAction.HARVEST, GathererJournal.Status.FARMING_HARVESTING
    );

    // Fallback work is done if there is no other work and no supplies available
    private static final ImmutableMap<FarmerJob.FarmerAction, GathererJournal.Status> FALLBACK_ITEMLESS_WORK = ImmutableMap.of(
            // Order here implies preference - which is important.
            FarmerJob.FarmerAction.WEED, GathererJournal.Status.FARMING_WEEDING,
            FarmerJob.FarmerAction.TILL, GathererJournal.Status.FARMING_TILLING
    );

    public static GathererJournal.@Nullable Status handleMorning(
            GathererJournal.Status currentStatus,
            TownProvider town,
            FarmStateProvider farm,
            EntityInvStateProvider inventory,
            boolean isInFarm
    ) {
        GathererJournal.Status status = JobStatuses.usualRoutine(
                currentStatus, false, inventory, new TownStateProvider() {
                    @Override
                    public boolean hasSupplies() {
                        return town.hasSupplies();
                    }

                    @Override
                    public boolean hasSpace() {
                        return town.hasSpace();
                    }

                    @Override
                    public boolean canUseMoreSupplies() {
                        return ITEM_WORK.keySet().stream().anyMatch(farm::isWorkPossible);
                    }
                },
                new JobStatuses.Job<GathererJournal.Status, GathererJournal.Status>() {
                    @Override
                    public GathererJournal.@Nullable Status tryChoosingItemlessWork() {
                        for (Map.Entry<FarmerJob.FarmerAction, GathererJournal.Status> s : PRIORITY_ITEMLESS_WORK.entrySet()) {
                            if (farm.isWorkPossible(s.getKey())) {
                                return JobsClean.doOrGoTo(
                                        s.getValue(), isInFarm, GathererJournal.Status.GOING_TO_JOBSITE
                                );
                            }
                        }
                        if (town.hasSupplies()) {
                            return null;
                        }
                        for (Map.Entry<FarmerJob.FarmerAction, GathererJournal.Status> s : FALLBACK_ITEMLESS_WORK.entrySet()) {
                            if (farm.isWorkPossible(s.getKey())) {
                                return JobsClean.doOrGoTo(
                                        s.getValue(), isInFarm, GathererJournal.Status.GOING_TO_JOBSITE
                                );
                            }
                        }
                        return null;
                    }

                    @Override
                    public @Nullable GathererJournal.Status tryUsingSupplies(
                            Map<GathererJournal.Status, Boolean> supplyItemStatus
                    ) {
                        for (Map.Entry<FarmerJob.FarmerAction, GathererJournal.Status> s : ITEM_WORK.entrySet()) {
                            if (supplyItemStatus.getOrDefault(s.getValue(), false)) {
                                if (farm.isWorkPossible(s.getKey())) {
                                    return JobsClean.doOrGoTo(
                                            s.getValue(), isInFarm, GathererJournal.Status.GOING_TO_JOBSITE
                                    );
                                }
                            }
                        }
                        return null;
                    }
                },
                GathererJournal.Status.FACTORY
        );
        if (isInFarm && ImmutableList.of(
                GathererJournal.Status.DROPPING_LOOT,
                GathererJournal.Status.COLLECTING_SUPPLIES
        ).contains(status)) {
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
