package ca.bradj.questown.jobs.declarative;

/** The status card's work bar: the share of a step's work done (dev playtest 2026-10-30). */
public final class WorkProgress {

    private WorkProgress() {
    }

    /** {@code 1 - left / total}, clamped to 0..1; -1 (no bar) when there is no work to show. */
    public static float fraction(int left, int total) {
        if (total <= 0 || left <= 0) {
            return -1;
        }
        return Math.max(0f, Math.min(1f, 1f - left / (float) total));
    }
}
