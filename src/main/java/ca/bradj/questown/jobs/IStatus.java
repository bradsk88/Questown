package ca.bradj.questown.jobs;

public interface IStatus<S extends IStatus<S>> {
    IStatusFactory<S> getFactory();

    boolean isGoingToJobsite();

    boolean isWorkingOnProduction();

    boolean isDroppingLoot();

    boolean isCollectingSupplies();

    String name();

    boolean isUnset();

    boolean isAllowedToTakeBreaks();
}
