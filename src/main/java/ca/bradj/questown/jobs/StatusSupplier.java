package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record StatusSupplier<STATUS>(
        STATUS targetStatus,
        Supplier<@Nullable WithReason<STATUS>> actualStatus
) {
    public static <STATUS> StatusSupplier<STATUS> found(STATUS s,
                                                        String reason
    ) {
        return new StatusSupplier<>(s, () -> new WithReason<>(s, reason));
    }
}
