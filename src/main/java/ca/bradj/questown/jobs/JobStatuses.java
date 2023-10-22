package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class JobStatuses {

    public interface Job<STATUS> {
        @Nullable STATUS tryChoosingItemlessWork();

        @Nullable STATUS tryUsingSupplies(Map<STATUS, Boolean> supplyItemStatus);
    }

    public static <STATUS extends IStatus<STATUS>> STATUS usualRoutine(
            STATUS currentStatus,
            EntityInvStateProvider<STATUS> inventory,
            TownStateProvider town,
            Job<STATUS> job,
            IStatusFactory<STATUS> factory
    ) {
        STATUS s = null;
        Map<STATUS, Boolean> supplyItemStatus = inventory.getSupplyItemStatus();
        boolean hasWorkItems = supplyItemStatus.values().stream().findAny().orElse(false);
        if (hasWorkItems) {
            STATUS useStatus = job.tryUsingSupplies(supplyItemStatus);
            if (useStatus != null) {
                s = useStatus;
            } else {
                hasWorkItems = false;
            }
        }
        if (s == null && inventory.inventoryFull()) {
            if (town.hasSpace()) {
                s = factory.droppingLoot();
            } else {
                s = factory.noSpace();
            }
        }

        if (s != null) {
            s = nullIfUnchanged(currentStatus, s);
            if (s != factory.goingToJobSite()) {
                return s;
            } else if (inventory.inventoryFull() || (inventory.hasItems() && !town.hasSupplies())) {
                return s;
            }
        }

        @Nullable STATUS s2 = job.tryChoosingItemlessWork();
        if (s2 != null) {
            return nullIfUnchanged(currentStatus, s2);
        } else if (inventory.hasNonSupplyItems()) {
            s2 = factory.droppingLoot();
        } else if (!town.hasSupplies()) {
            if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.noSupplies());
            } else {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            }
        } else {
            if (inventory.hasItems() && !hasWorkItems) {
                s2 = nullIfUnchanged(currentStatus, factory.droppingLoot());
            }
            else if (town.canUseMoreSupplies()) {
                s2 = nullIfUnchanged(currentStatus, factory.collectingSupplies());
            } else {
                return factory.idle();
            }
        }

        if (s2 != factory.collectingSupplies() && s != null) {
            return s;
        }

        return s2;
    }


    private static <S> S nullIfUnchanged(
            S oldStatus,
            S newStatus
    ) {
        if (oldStatus == newStatus) {
            return null;
        }
        return newStatus;
    }
}
