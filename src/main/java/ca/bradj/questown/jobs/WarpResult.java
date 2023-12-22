package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.TownState;
import com.google.common.collect.ImmutableList;

public record WarpResult<H, TOWN extends TownState<?, ?, ?, ?, ?>>(
        ProductionStatus status,
        ImmutableList<H> items,
        TOWN townState
) {
}
