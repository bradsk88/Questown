package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.Signals;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Which villagers had no bed for the night just ended. Recorded at nightfall, used once at dawn for the sleepless effect. */
public final class BedlessLedger {

    private @Nullable Signals lastSignals = null;
    private boolean captureDue = false;
    private final Set<UUID> pending = new HashSet<>();

    /** True when the bed check should run: at nightfall, or later that night once beds are known. A check still pending at dawn is dropped. */
    public boolean captureEdge(Signals signals, boolean bedsKnown) {
        boolean edge = signals == Signals.EVENING
                && lastSignals != Signals.EVENING
                && lastSignals != Signals.NIGHT;
        lastSignals = signals;
        if (edge) {
            captureDue = true;
        }
        if (signals != Signals.EVENING && signals != Signals.NIGHT) {
            captureDue = false;
        }
        if (!captureDue || !bedsKnown) {
            return false;
        }
        captureDue = false;
        return true;
    }

    /** Records the villagers without a bed (replacing any earlier record). */
    public void recordBedless(Iterable<UUID> bedlessUuids) {
        pending.clear();
        bedlessUuids.forEach(pending::add);
    }

    /** Takes the record and clears it. */
    public Set<UUID> drain() {
        Set<UUID> out = new HashSet<>(pending);
        pending.clear();
        return out;
    }
}
