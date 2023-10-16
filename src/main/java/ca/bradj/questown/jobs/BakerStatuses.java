package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class BakerStatuses {

    public interface TownStateProvider<ROOM extends Room> extends ca.bradj.questown.jobs.TownStateProvider {
        boolean isBakeryFull(RoomRecipeMatch<ROOM> room);

        boolean hasBakerySpace();

        Collection<ROOM> bakeriesWithBread();
    }

    public interface EntityStateProvider<ROOM extends Room> {
        @Nullable RoomRecipeMatch<ROOM> getEntityBakeryLocation();
    }

    public static @Nullable <ROOM extends Room> GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            Signals signal,
            ca.bradj.questown.jobs.EntityStateProvider inventory,
            TownStateProvider<ROOM> town,
            EntityStateProvider<ROOM> entity
    ) {
//        if (!inventory.isValid()) {
//            throw new IllegalStateException("Inventory state is invalid");
//        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING, NOON -> {
                // TODO: Different logic depending on time of day
                return getMorningStatus(currentStatus, inventory, town, entity);
            }
            case NIGHT, EVENING -> {
                return getEveningStatus(currentStatus, inventory, town);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    public static <ROOM extends Room> GathererJournal.@Nullable Status getMorningStatus(
            GathererJournal.Status currentStatus,
            ca.bradj.questown.jobs.EntityStateProvider inventory,
            TownStateProvider<ROOM> town,
            EntityStateProvider<ROOM> entity
    ) {
        GathererJournal.Status newStatus = JobStatuses.usualRoutine(currentStatus, inventory, town,
                new JobStatuses.Job() {
                    @Override
                    public GathererJournal.@Nullable Status tryDoingSpecializedWork() {
                        Collection<ROOM> breads = town.bakeriesWithBread();
                        if (!breads.isEmpty()) {
                            return GathererJournal.Status.COLLECTING_BREAD;
                        }
                        return null;
                    }

                    @Override
                    public @Nullable GathererJournal.Status tryUsingSupplies(
                            Map<GathererJournal.Status, Boolean> supplyItemStatus
                    ) {
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.BAKING, false)) {
                            if (entity.getEntityBakeryLocation() != null) {
                                return GathererJournal.Status.BAKING;
                            }
                        }
                        if (supplyItemStatus.getOrDefault(GathererJournal.Status.GOING_TO_BAKERY, false)) {
                            if (town.hasBakerySpace()) {
                                return GathererJournal.Status.GOING_TO_BAKERY;
                            }
                        }
                        return null;
                    }
                }
        );
        return nullIfUnchanged(currentStatus, newStatus);
    }

    public static <ROOM extends Room> GathererJournal.@Nullable Status getEveningStatus(
            GathererJournal.Status currentStatus,
            ca.bradj.questown.jobs.EntityStateProvider inventory,
            TownStateProvider<ROOM> town
    ) {
        Collection<ROOM> breads = town.bakeriesWithBread();
        if (!breads.isEmpty()) {
            return GathererJournal.Status.COLLECTING_BREAD;
        }

        if (inventory.hasItems()) {
            return nullIfUnchanged(currentStatus, GathererJournal.Status.DROPPING_LOOT);
        }

        return nullIfUnchanged(currentStatus, GathererJournal.Status.RELAXING);
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
