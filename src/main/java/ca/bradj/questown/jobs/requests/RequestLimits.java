package ca.bradj.questown.jobs.requests;

import java.util.HashMap;
import java.util.Map;

/**
 * How much of each requested item storage may hold before its board request pauses (BoardRequests).
 * Was a fixed 8 for every request; the dev wants to see and adjust it per request (playtest
 * 2026-10-29: "the player needs some way to see 'we've hit the limit' and to see and adjust the
 * limit"). Unset requests use {@link BoardRequests#PAUSE_AT}.
 */
public final class RequestLimits<R> {

    public static final int MIN = 1;
    public static final int MAX = 64;

    private final Map<R, Integer> limits = new HashMap<>();

    public int get(R request) {
        return limits.getOrDefault(request, BoardRequests.PAUSE_AT);
    }

    /** Sets the limit, clamped to {@link #MIN}..{@link #MAX}; returns what was stored. */
    public int set(R request, int limit) {
        int clamped = Math.max(MIN, Math.min(MAX, limit));
        limits.put(request, clamped);
        return clamped;
    }

    public void forget(R request) {
        limits.remove(request);
    }

    public Map<R, Integer> asMap() {
        return Map.copyOf(limits);
    }
}
