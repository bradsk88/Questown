package ca.bradj.questown.town.entity;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/** Paces the flag's chat lines: the first at once, the rest an interval apart, repeats dropped. */
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
        // The line just sent counts as a repeat too.
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
