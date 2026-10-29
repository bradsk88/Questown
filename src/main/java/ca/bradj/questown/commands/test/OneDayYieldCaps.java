package ca.bradj.questown.commands.test;

/**
 * The most loot one warp day may bank in the one-day yield autotests: a warp must not out-produce
 * realtime, and realtime's ceiling is the most generous trip tier repeated across a day's
 * productive ticks. Pinned against the job data by {@code OneDayYieldCapsTest}, so a change to
 * trip yields fails a unit test instead of turning the autotest into a flake.
 */
final class OneDayYieldCaps {

    /** A warp day is 24000 ticks, at most half of them productive. */
    static final int PRODUCTIVE_TICKS = 12000;

    /** hunter: sword_half_day, 5 attempts per 4000 ticks, 3 trips (Outfitter ruling #25, 2026-10-26). */
    static final int HUNTER = 15;

    /** gatherer: axe_half_day, 5 attempts per 4000 ticks, 3 trips (Outfitter ruling #25, 2026-10-26). */
    static final int GATHERER = 15;

    private OneDayYieldCaps() {
    }

    static int capFor(
            int attempts,
            int ticksPerTrip
    ) {
        return attempts * (PRODUCTIVE_TICKS / ticksPerTrip);
    }
}
