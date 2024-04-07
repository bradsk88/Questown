package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.JobStatuses;
import ca.bradj.questown.jobs.LegacyJob;
import com.google.common.collect.ImmutableList;

public interface IProductionJob<STATUS> extends LegacyJob<STATUS, Integer> {
    /**
     * The most-preferred status should be the first entry in the list
     */
    ImmutableList<Integer> getAllWorkStatesSortedByPreference();
}
