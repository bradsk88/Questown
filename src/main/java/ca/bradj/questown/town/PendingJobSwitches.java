package ca.bradj.questown.town;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Job switches the player asked for "after this trip", applied at the villager's next job pick. The latest wins. Not saved. */
public final class PendingJobSwitches<V, J> {

    private final Map<V, J> pending = new HashMap<>();

    public void request(V villager, J job) {
        pending.put(villager, job);
    }

    /** The pending switch, or null; it stays until it happens. */
    public @Nullable J peek(V villager) {
        return pending.get(villager);
    }

    /** The switch to apply now, or null. */
    public @Nullable J take(V villager) {
        return pending.remove(villager);
    }
}
