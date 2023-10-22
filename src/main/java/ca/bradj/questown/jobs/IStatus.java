package ca.bradj.questown.jobs;

public interface IStatus<S extends IStatus<S>> {
    IStatusFactory<S> getFactory();
}
