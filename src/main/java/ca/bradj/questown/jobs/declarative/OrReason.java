package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.WorkOutput;
import ca.bradj.questown.jobs.WorkSpot;
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

    public static <TOWN, POS> OrReason<WorkOutput<TOWN, WorkSpot<Integer, POS>>> reason(
            String format,
            Object... args
    ) {
        return reason(String.format(format, args));
    }
}
