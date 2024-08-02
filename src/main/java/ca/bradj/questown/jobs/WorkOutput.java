package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public record WorkOutput<TOWN, POS>(
        boolean worked,
        boolean claimed,
        @Nullable TOWN town,
        POS spot
) {
}
