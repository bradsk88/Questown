package ca.bradj.questown.gui.town.status;

import ca.bradj.questown.gui.StatusPacket;
import com.google.common.collect.EvictingQueue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-villager status smoothing for the villagers page: each frame shows the most frequent status
 * of the last {@link #WINDOW} frames, so a status that flickers for a frame doesn't flash.
 * <p>
 * The window must stay bounded. An unbounded history made the page show the all-time most
 * frequent status since it opened: a returning hunter never showed "dropping loot", and the
 * leaver-return bar stayed full after the next trip began (2026-10-21 playtest).
 */
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

    /**
     * {@link StatusPacket#equals} ignores the leaver-return fields, so two equal packets can carry
     * different trips. The newest one has the current trip.
     */
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
