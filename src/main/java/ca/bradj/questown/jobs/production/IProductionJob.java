package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.LegacyJob;
import com.google.common.collect.ImmutableList;

public interface IProductionJob<STATUS> extends LegacyJob<STATUS, STATUS> {
    /**
     * The most-preferred status should be the first entry in the list
     */
    ImmutableList<STATUS> getAllWorkStatesSortedByPreference();

    STATUS getMaxState();
}
