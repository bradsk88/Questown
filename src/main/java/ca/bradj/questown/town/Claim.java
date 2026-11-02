package ca.bradj.questown.town;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Claim(
        UUID owner,
        long ticksLeft
) {
    /** Ages the claim by {@code delta} ticks; null once it expires, which frees a claim whose owner is gone. */
    @Nullable
    public Claim ticked(long delta) {
        long remaining = ticksLeft - delta;
        return remaining <= 0 ? null : new Claim(owner, remaining);
    }
}
