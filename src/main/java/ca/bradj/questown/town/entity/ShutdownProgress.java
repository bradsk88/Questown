package ca.bradj.questown.town.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure bookkeeping for a town-shutdown ritual: which roster members have been absorbed into the
 * flag, and whether the ritual is complete or a straggler is now due for force-absorb.
 *
 * <p>No Minecraft types — this is the testable decision core of {@link TownShutdownController}.
 * Completion is gated on <em>both</em> the recall finishing (every roster member absorbed) and a
 * minimum-duration floor, so even a one-townie town cannot pack up reactively (ADR-0009).
 */
class ShutdownProgress {

    private final Set<UUID> roster;
    private final Set<UUID> absorbed = new HashSet<>();
    private final long startTick;
    private final long floorTicks;
    private final long forceAbsorbTicks;

    ShutdownProgress(
            Set<UUID> roster,
            long startTick,
            long floorTicks,
            long forceAbsorbTicks
    ) {
        this.roster = new HashSet<>(roster);
        this.startTick = startTick;
        this.floorTicks = floorTicks;
        this.forceAbsorbTicks = forceAbsorbTicks;
    }

    void markAbsorbed(UUID vuid) {
        absorbed.add(vuid);
    }

    /**
     * The ritual is done when every roster member has been absorbed AND the minimum-duration floor
     * has elapsed. The floor is the anti-cheat property: it makes shutdown deliberately slow.
     */
    boolean isComplete(long now) {
        return absorbed.containsAll(roster) && elapsed(now) >= floorTicks;
    }

    /**
     * Once this deadline passes, any townie still in the world is absorbed where it stands. This is
     * the deadlock guard: a townie that cannot path home (walled off, stuck) must not be able to
     * strand the ritual forever.
     */
    boolean forceAbsorbDue(long now) {
        return elapsed(now) >= forceAbsorbTicks;
    }

    private long elapsed(long now) {
        return now - startTick;
    }
}
