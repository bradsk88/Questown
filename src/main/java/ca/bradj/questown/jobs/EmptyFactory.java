package ca.bradj.questown.jobs;

public interface EmptyFactory<I extends GathererJournal.Item<I>> {
    I makeEmptyItem();
}
