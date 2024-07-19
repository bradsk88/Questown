package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableSet;

public class NotValidCoreStatus extends RuntimeException {
    public NotValidCoreStatus(
            String name,
            ImmutableSet<ProductionStatus> all
    ) {
        super(
                name + " is not a valid core state. " +
                        "Must be one of [" + String.join(
                        ", ",
                        all.stream().map(v -> v.name).toList()
                ) + "]"
        );
    }
}
