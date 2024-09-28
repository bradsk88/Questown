package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class JobStatusObject<STATUS> implements Supplier<@Nullable STATUS> {

    private final List<LZCD<STATUS>> orderedStatusSuppliers;

    private record Sources<STATUS>(
            JobStatuses.Job<STATUS, ?> job
    ) {
    }

    public JobStatusObject(List<LZCD<STATUS>> orderedStatusSuppliers) {
        this.orderedStatusSuppliers = orderedStatusSuppliers;
    }

    public @Nullable STATUS get() {
        for (LZCD<STATUS> l : orderedStatusSuppliers) {
            STATUS v = l.resolve();
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
