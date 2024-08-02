package ca.bradj.questown.jobs;

public record WorkedSpot<POS>(
        POS workPosition,
        Integer stateAfterWork
) {
}
