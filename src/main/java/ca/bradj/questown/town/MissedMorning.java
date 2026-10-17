package ca.bradj.questown.town;

import ca.bradj.questown.jobs.Signals;

/**
 * Whether a flag that was not ticking (no player in range) slept through a morning that its own
 * MORNING-signal detection will not catch on resume (playtest 2026-10-17: a BOP job change queued
 * for "the morning" never applied after the player was away overnight).
 *
 * <p>The ticker runs the morning pass when it sees the MORNING window ({@code dayTime % 24000 <
 * 6000}) after a non-morning tick. A resume inside that window is caught by the ticker, unless the
 * flag also stopped inside a morning window (its "already morning" latch is still set).
 */
public final class MissedMorning {
    private MissedMorning() {
    }

    public static boolean missedDuringAbsence(long fromDayTime, long toDayTime) {
        if (toDayTime <= fromDayTime) {
            return false;
        }
        long dawnsCrossed = Math.floorDiv(toDayTime, Signals.FULL_DAY_TICKS)
                - Math.floorDiv(fromDayTime, Signals.FULL_DAY_TICKS);
        if (dawnsCrossed < 1) {
            return false;
        }
        boolean resumedPastMorning = !isMorningWindow(toDayTime);
        boolean stoppedInMorning = isMorningWindow(fromDayTime);
        return resumedPastMorning || stoppedInMorning;
    }

    private static boolean isMorningWindow(long dayTime) {
        return Signals.fromDayTime(new Signals.DayTime(dayTime)) == Signals.MORNING;
    }
}
