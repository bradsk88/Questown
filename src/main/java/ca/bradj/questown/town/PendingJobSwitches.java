package ca.bradj.questown.town;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Job switches the player asked to apply "after this trip" (dev playtest 2026-10-29): a villager
 * away on a trip keeps it, and the board applies the switch at its next job pick. The latest request
 * per villager wins. Not saved: a reload drops a pending switch (the villager keeps its job).
 */
public final class PendingJobSwitches<V, J> {

    private final Map<V, J> pending = new HashMap<>();

    public void request(V villager, J job) {
        pending.put(villager, job);
    }

    /** The pending switch, or null, left in place (the villager card shows it until it happens). */
    public @Nullable J peek(V villager) {
        return pending.get(villager);
    }

    /** The switch to apply now, or null; it is forgotten once taken. */
    public @Nullable J take(V villager) {
        return pending.remove(villager);
    }
}
