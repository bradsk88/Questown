package ca.bradj.questown.town.entity;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * The flag-wide chat pacing of {@link TownMessages}: the first line goes out at once, the rest
 * wait for a free slot one interval apart, and consecutive duplicates are dropped.
 */
final class BroadcastPacer<T> {

    enum Outcome {SENT, QUEUED, DROPPED}

    private final long spacingTicks;
    private final ArrayDeque<T> pending = new ArrayDeque<>();
    private long lastSentAt = Long.MIN_VALUE;
    private T lastSent;

    BroadcastPacer(long spacingTicks) {
        this.spacingTicks = spacingTicks;
    }

    Outcome offer(T line, long now, Consumer<T> send) {
        if (slotFree(now)) {
            send(line, now, send);
            return Outcome.SENT;
        }
        // The line just sent counts too: a room-destroyed event fires three times in one tick,
        // and comparing only the queue let the second copy through (2026-10-22 r6).
        if (line.equals(lastSent) || pending.contains(line)) {
            return Outcome.DROPPED;
        }
        pending.addLast(line);
        return Outcome.QUEUED;
    }

    void flush(long now, Consumer<T> send) {
        if (pending.isEmpty() || !slotFree(now)) {
            return;
        }
        send(pending.pollFirst(), now, send);
    }

    private void send(T line, long now, Consumer<T> send) {
        lastSentAt = now;
        lastSent = line;
        send.accept(line);
    }

    void clear() {
        pending.clear();
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    private boolean slotFree(long now) {
        return lastSentAt == Long.MIN_VALUE || now - lastSentAt >= spacingTicks;
    }
}
