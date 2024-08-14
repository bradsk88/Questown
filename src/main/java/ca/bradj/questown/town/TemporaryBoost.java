package ca.bradj.questown.town;

public record TemporaryBoost(
        double factor,
        long ticksLeft
) {
    public static TemporaryBoost done() {
        return new TemporaryBoost(1, 0);
    }

    public TemporaryBoost ticked() {
        return new TemporaryBoost(factor, ticksLeft - 1);
    }

    public TemporaryBoost rewind() {
        return new TemporaryBoost(factor, ticksLeft + 1);
    }

    public boolean isDone() {
        return ticksLeft <= 0;
    }
}
