package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface IProductionJob<STATUS> extends JobStatuses.Job<STATUS> {
    /**
     * The most-preferred status should be the first entry in the list
     */
    ImmutableList<STATUS> getAllWorkStatusesSortedByPreference();
}
