package ca.bradj.questown.jobs.declarative;

import org.jetbrains.annotations.Nullable;

public record OrReason<X>(
        @Nullable X value,
        @Nullable String reason
) {
    public static <TOWN> OrReason<TOWN> reason(String reason) {
        return new OrReason<>(null, reason);
    }

    public static <TOWN> OrReason<TOWN> success(TOWN town) {
        return new OrReason<>(town, null);
    }
}
