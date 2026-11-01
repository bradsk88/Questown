package ca.bradj.questown.jobs;

/**
 * A timed leaver trip currently in flight: the absolute server tick at which
 * the gate-mat timer expires (the point the townie is clear to re-enter town)
 * and the trip's declared total duration in ticks. The villagers-menu payload
 * carries this; the client derives the progress fraction from its own clock,
 * so the bar ticks down live while the menu is open (LAN: client and server
 * clocks agree to within a few ticks).
 */
public record LeaverReturn(
        long returnTick,
        int totalTicks
) {
}
