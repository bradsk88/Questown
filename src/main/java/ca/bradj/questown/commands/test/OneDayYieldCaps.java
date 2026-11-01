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

    /**
     * hunter: the best day is two sword_full_day trips (8 attempts per 6000 ticks) with every roll
     * a meat, 2 per roll: 2 x 8 x 2 = 32.
     */
    static final int HUNTER = 32;

    /** The most items one hunter roll gives. */
    static final int HUNTER_ITEMS_PER_ROLL = 2;

    /** gatherer: axe_half_day, 5 attempts per 4000 ticks, 3 trips. */
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
