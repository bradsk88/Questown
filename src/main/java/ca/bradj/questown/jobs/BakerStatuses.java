package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;

public class BakerStatuses {

    public interface InventoryStateProvider {
        boolean inventoryFull();
        boolean hasNonSupplyItems();
    }

    public interface TownStateProvider<ROOM extends Room> {
        boolean hasSupplies();
        boolean isBakeryFull(RoomRecipeMatch<ROOM> room);
        boolean hasBakerySpace();
    }

    public interface EntityStateProvider<ROOM extends Room> {
        @Nullable RoomRecipeMatch<ROOM> getEntityBakeryLocation();
    }

    public static @Nullable <ROOM extends Room> GathererJournal.Status getNewStatusFromSignal(
            GathererJournal.Status currentStatus,
            Signals signal,
            InventoryStateProvider inventory,
            TownStateProvider<ROOM> town,
            EntityStateProvider<ROOM> entity
    ) {
//        if (!inventory.isValid()) {
//            throw new IllegalStateException("Inventory state is invalid");
//        }
        switch (signal) {
//            GathererJournal.Status status = null;
            case MORNING, NIGHT, EVENING, NOON -> {
                // TODO: Different logic depending on time of day
                return handleMorning(currentStatus, inventory, town, entity);
            }
            default -> throw new IllegalArgumentException(String.format("Unrecognized signal %s", signal));
        }
    }

    private static <ROOM extends Room> GathererJournal.@Nullable Status handleMorning(
            GathererJournal.Status currentStatus,
            InventoryStateProvider inventory,
            TownStateProvider<ROOM> town,
            EntityStateProvider<ROOM> entity
    ) {
        if (inventory.hasNonSupplyItems()) {
            return nullIfUnchanged(currentStatus, GathererJournal.Status.DROPPING_LOOT);
        }
        if (inventory.inventoryFull()) {
            if (entity.getEntityBakeryLocation() != null) {
                return nullIfUnchanged(currentStatus, GathererJournal.Status.BAKING);
            }
            if (town.hasBakerySpace()) {
                return nullIfUnchanged(currentStatus, GathererJournal.Status.GOING_TO_BAKERY);
            }
            return GathererJournal.Status.DROPPING_LOOT;
        }

        if (!town.hasSupplies()) {
            return nullIfUnchanged(currentStatus, GathererJournal.Status.NO_SUPPLIES);
        }

        return nullIfUnchanged(currentStatus, GathererJournal.Status.COLLECTING_SUPPLIES);
    }

    private static GathererJournal.Status nullIfUnchanged(
            GathererJournal.Status oldStatus, GathererJournal.Status newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}
