package ca.bradj.questown.jobs;

/** A trip in progress: the server tick it ends at, and its total ticks. */
public record LeaverReturn(
        long returnTick,
        int totalTicks
) {
}
