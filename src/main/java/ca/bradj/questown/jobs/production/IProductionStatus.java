package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.IStatus;

public interface IProductionStatus<S extends IProductionStatus<S>> extends IStatus<S> {
    boolean isInsertingIngredients();

    boolean isWorkingOnProduction();

    boolean isExtractingProduct();
}

