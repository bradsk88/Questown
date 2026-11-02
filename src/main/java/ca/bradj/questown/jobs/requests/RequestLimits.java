package ca.bradj.questown.jobs.requests;

import java.util.HashMap;
import java.util.Map;

/** How much of each requested item storage may hold before its request pauses; unset requests use {@link BoardRequests#PAUSE_AT}. */
public final class RequestLimits<R> {

    public static final int MIN = 1;
    public static final int MAX = 64;

    private final Map<R, Integer> limits = new HashMap<>();

    public int get(R request) {
        return limits.getOrDefault(request, BoardRequests.PAUSE_AT);
    }

    public int set(R request, int limit) {
        int clamped = Math.max(MIN, Math.min(MAX, limit));
        limits.put(request, clamped);
        return clamped;
    }

    /** Moves the limit by {@code delta}, clamped; returns the new limit. */
    public int adjust(R request, int delta) {
        return set(request, get(request) + delta);
    }

    public void forget(R request) {
        limits.remove(request);
    }

    public Map<R, Integer> asMap() {
        return Map.copyOf(limits);
    }
}
