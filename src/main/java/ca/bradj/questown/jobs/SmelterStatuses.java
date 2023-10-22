package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class SmelterStatuses {

    public interface TownStateProvider<ROOM extends Room> extends TownProvider {
        Collection<ROOM> roomsWithCompletedProduct();

        Map<SmelterStatus, ? extends Collection<ROOM>> roomsNeedingIngredients();
    }

    public static @Nullable <ROOM extends Room> SmelterStatus getNewStatusFromSignal(
            SmelterStatus currentStatus,
            Signals signal,
            EntityInvStateProvider<SmelterStatus> inventory,
            TownStateProvider<ROOM> town,
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
            TownStateProvider<ROOM> town,
            EntityLocStateProvider<ROOM> entity
    ) {
        SmelterStatus newStatus = JobStatuses.usualRoutine(
                currentStatus, inventory, new ca.bradj.questown.jobs.TownStateProvider() {
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
                        return !town.roomsNeedingIngredients().entrySet().stream().allMatch(v -> v.getValue().isEmpty());
                    }
                },
                new JobStatuses.Job<>() {
                    @Override
                    public @Nullable SmelterStatus tryChoosingItemlessWork() {
                        Collection<ROOM> rp = town.roomsWithCompletedProduct();
                        if (!rp.isEmpty()) {
                            return SmelterStatus.FACTORY.collectingFinishedProduct();
                        }
                        return null;
                    }

                    @Override
                    public @Nullable SmelterStatus tryUsingSupplies(
                            Map<SmelterStatus, Boolean> supplyItemStatus
                    ) {
                        RoomRecipeMatch<ROOM> location = entity.getEntityCurrentJobSite();
                        for (Map.Entry<SmelterStatus, Boolean> s : supplyItemStatus.entrySet().stream().filter(Map.Entry::getValue).toList()) {
                            if (town.roomsNeedingIngredients().containsKey(s.getKey())) {
                                boolean inSite = false;
                                if (location != null) {
                                    inSite = town.roomsNeedingIngredients().get(s.getKey()).contains(location.room);
                                }
                                return JobsClean.doOrGoTo(s.getKey(), inSite, SmelterStatus.GOING_TO_JOBSITE);
                            }
                        }
                        return null;
                    }
                },
                SmelterStatus.FACTORY
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    public static <ROOM extends Room> @Nullable SmelterStatus getEveningStatus(
            SmelterStatus currentStatus,
            EntityInvStateProvider<SmelterStatus> inventory,
            TownStateProvider<ROOM> town
    ) {
//        Collection<ROOM> breads = town.roomsWithCompletedProduct();
//        if (!breads.isEmpty()) {
//            return SmelterStatus.COLLECTING_BREAD;
//        }

        if (inventory.hasItems()) {
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
