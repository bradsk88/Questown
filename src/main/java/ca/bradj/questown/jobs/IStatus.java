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

    /**
     * A downtime townie's job has no server-side status (null). For
     * job-assignment purposes a null status counts as "not busy": board
     * request re-evaluations must reach the downtime pool rather than NPE.
     */
    static boolean isBusy(@Nullable IStatus<?> status) {
        return status != null && status.isBusy();
    }
}
