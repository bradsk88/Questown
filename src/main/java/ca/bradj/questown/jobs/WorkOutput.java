package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public record WorkOutput<TOWN, POS>(
        boolean worked,
        @Nullable TOWN town,
        POS spot
) {
}
