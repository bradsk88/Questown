package ca.bradj.questown.town.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks a town shutdown: which villagers are taken in, and whether it's done. It finishes only once all are in and a minimum time has passed. */
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

    /** Done when every villager is in and the minimum time has passed. */
    boolean isComplete(long now) {
        return absorbed.containsAll(roster) && elapsed(now) >= floorTicks;
    }

    /** Past this point, any villager still out is taken in where it stands, so one that can't get home can't stall the shutdown. */
    boolean forceAbsorbDue(long now) {
        return elapsed(now) >= forceAbsorbTicks;
    }

    private long elapsed(long now) {
        return now - startTick;
    }
}
