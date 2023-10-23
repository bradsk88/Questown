package ca.bradj.questown.jobs;

public interface EmptyFactory<I extends Item<I>> {
    I makeEmptyItem();
}
