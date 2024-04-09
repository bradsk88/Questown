package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.IStatus;

public interface IProductionStatus<S extends IProductionStatus<S>> extends IStatus<S>, Comparable<S> {

    boolean isWorkingOnProduction();

    boolean isExtractingProduct();

    boolean isWaitingForTimers();

    int value();
}

