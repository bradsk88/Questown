package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface JournalItemsListener<I> {
    void itemsChanged(ImmutableList<I> items);
}
