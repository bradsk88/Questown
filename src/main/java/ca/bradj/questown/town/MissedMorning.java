package ca.bradj.questown.town;

import ca.bradj.questown.jobs.Signals;

/** Whether a flag that wasn't ticking slept through a morning its own detection will miss on resume. */
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
