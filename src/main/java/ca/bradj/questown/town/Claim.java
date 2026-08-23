package ca.bradj.questown.town;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Claim(
        UUID owner,
        long ticksLeft
) {
    /**
     * Advance the claim by {@code delta} game ticks, returning the next claim — or {@code null}
     * once its TTL runs out so the caller can drop it. The TTL is the catch-all that releases a
     * claim orphaned by an owner that died, unloaded, changed jobs, or got stuck without ever
     * reaching the work-cycle reset that normally clears it; without it the spot would be blocked
     * from every other townie for the rest of the world's life.
     */
    @Nullable
    public Claim ticked(long delta) {
        long remaining = ticksLeft - delta;
        return remaining <= 0 ? null : new Claim(owner, remaining);
    }
}
