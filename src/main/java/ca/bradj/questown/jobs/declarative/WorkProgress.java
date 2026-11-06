package ca.bradj.questown.jobs.declarative;

public final class WorkProgress {

    private WorkProgress() {
    }

    /** {@code 1 - left / total}, within 0 to 1; -1 when there is no work to show. */
    public static float fraction(int left, int total) {
        if (total <= 0 || left <= 0) {
            return -1;
        }
        return Math.max(0f, Math.min(1f, 1f - left / (float) total));
    }
}
