package ca.bradj.questown.town;

import java.util.UUID;

public record Claim(
        UUID owner,
        long ticksLeft
) {
    public Claim ticked() {
        return new Claim(owner, ticksLeft - 1);
    }
}
