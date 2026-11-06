package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.Signals;

/** The "Your camp is ready" toast asks for a campfire sleep, so it hides at dawn and comes back in the evening. */
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
