package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.Signals;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-town record of which townies had <em>no bed</em> for the night that just ended.
 * Pure and Forge-free, so the capture/drain rule is unit tested without a flag or a level.
 *
 * <p>Capture happens on the <strong>nightfall edge</strong> — the first tick whose
 * {@link Signals} is {@code EVENING} (tick 11500, before the villager schedule's
 * REST phase begins at 12500). The townies without an assigned bed at that moment
 * are recorded; the set is drained exactly once at morning, when the {@code SLEEPLESS}
 * mood effect is applied to each recorded townie. A townie that gains a bed later in
 * the evening (after the capture) is still recorded — v1 trade-off, see
 * {@code docs/plans/2026-10-09-003-feat-sleepless-no-bed-debuff-plan.md}.
 */
public final class BedlessLedger {

    private @Nullable Signals lastSignals = null;
    private boolean captureDue = false;
    private final Set<UUID> pending = new HashSet<>();

    /**
     * Note the current tick's signal. Returns true when the capture should run now: on the
     * nightfall edge (the first {@code EVENING} tick after the signal was anything but
     * {@code EVENING}/{@code NIGHT}), or later that night once {@code bedsKnown}. A restart
     * mid-night therefore does not capture until the next real evening; a restart
     * mid-evening captures once the restarted town has matched its rooms. Before that
     * every townie reads as bedless (2026-10-22 playtest). A capture still due at dawn is
     * dropped: that night goes unrecorded.
     */
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

    /** Record the townies without a bed at the nightfall edge (replaces any earlier capture). */
    public void recordBedless(Iterable<UUID> bedlessUuids) {
        pending.clear();
        bedlessUuids.forEach(pending::add);
    }

    /** Take out the recorded set (empty when none were recorded) and clear it. */
    public Set<UUID> drain() {
        Set<UUID> out = new HashSet<>(pending);
        pending.clear();
        return out;
    }
}
