package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface IStatus<S extends IStatus<S>> {
    IStatusFactory<S> getFactory();

    boolean isGoingToJobsite();

    boolean isDroppingLoot();

    boolean isCollectingSupplies();

    String name();
    String nameV2();

    boolean isUnset();

    boolean isAllowedToTakeBreaks();

    @Nullable String getCategoryId();

    boolean isBusy();

    /** A villager on downtime has no status; it counts as not busy. */
    static boolean isBusy(@Nullable IStatus<?> status) {
        return status != null && status.isBusy();
    }
}
