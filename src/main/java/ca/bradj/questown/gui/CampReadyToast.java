package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.Signals;

/**
 * "Your camp is ready / Use wand on campfire" asks for a campfire sleep, which can't start in
 * daylight. A player who didn't sleep kept it on screen the whole next day (2026-10-22 r3 #17), so
 * it hides once morning comes; the server re-arms it for the next evening.
 */
public final class CampReadyToast {

    public static final String TITLE_KEY = "tutorial.campfire_ready.title";

    private CampReadyToast() {
    }

    public static boolean hideNow(
            boolean isCampReadyToast,
            long dayTime
    ) {
        if (!isCampReadyToast) {
            return false;
        }
        Signals s = Signals.fromDayTime(new Signals.DayTime(dayTime));
        return s == Signals.MORNING || s == Signals.NOON;
    }
}
