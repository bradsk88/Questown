package ca.bradj.questown.gui;

import java.util.List;

/** The buttons of {@link SwitchJobConfirmScreen}, in order. */
public enum SwitchJobChoice {
    AFTER_TRIP("menu.switch_job.after_trip"),
    NOW_LOSE_TRIP("menu.switch_job.now_lose_trip"),
    NOW("menu.switch_job.now"),
    CANCEL("menu.common.cancel");

    private final String key;

    SwitchJobChoice(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Away on a trip: after the trip, or now (the trip is lost). Otherwise: now. Cancel last. */
    public static List<SwitchJobChoice> forTrip(boolean awayOnTrip) {
        if (awayOnTrip) {
            return List.of(AFTER_TRIP, NOW_LOSE_TRIP, CANCEL);
        }
        return List.of(NOW, CANCEL);
    }
}
