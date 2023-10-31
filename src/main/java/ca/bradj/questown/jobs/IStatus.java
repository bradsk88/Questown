package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface IStatus<S extends IStatus<S>> {
    IStatusFactory<S> getFactory();

    boolean isGoingToJobsite();

    boolean isDroppingLoot();

    boolean isCollectingSupplies();

    String name();

    boolean isUnset();

    boolean isAllowedToTakeBreaks();

    @Nullable String getCategoryId();
}
