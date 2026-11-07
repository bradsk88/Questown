package ca.bradj.questown.gui.town.status;

import ca.bradj.questown.gui.StatusPacket;
import com.google.common.collect.EvictingQueue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shows each villager's most frequent status of the last {@link #WINDOW} frames, so a one-frame flicker doesn't flash. */
final class StatusSmoother {

    private static final int WINDOW = 5;

    private final Map<UUID, EvictingQueue<StatusPacket>> recent = new HashMap<>();

    StatusPacket smooth(
            UUID villager,
            StatusPacket latest
    ) {
        EvictingQueue<StatusPacket> q = recent.computeIfAbsent(villager, k -> EvictingQueue.create(WINDOW));
        q.add(latest);
        Map<StatusPacket, Integer> counts = new HashMap<>();
        for (StatusPacket p : q) {
            counts.merge(p, 1, Integer::sum);
        }
        StatusPacket mode = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
        return newestEqualTo(q, mode);
    }

    /** Equal packets can differ in their trip, so take the newest. */
    private static StatusPacket newestEqualTo(
            Iterable<StatusPacket> q,
            StatusPacket mode
    ) {
        StatusPacket newest = mode;
        for (StatusPacket p : q) {
            if (p.equals(mode)) {
                newest = p;
            }
        }
        return newest;
    }
}
