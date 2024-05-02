package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public record StatusSupplier<STATUS>(
        STATUS targetStatus,
        Supplier<@Nullable STATUS> actualStatus
) {
    public static <STATUS> StatusSupplier<STATUS> found(STATUS s) {
        return new StatusSupplier<>(s, () -> s);
    }
}
