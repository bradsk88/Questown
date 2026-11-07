package ca.bradj.questown.town.quests;

import java.util.Collection;

/** Rules for the morning reward queue. */
public final class MorningRewards {

    private MorningRewards() {
    }

    /** Whether any morning reward is queued. */
    public static boolean isAnyPending(Collection<?> queuedRewards) {
        return !queuedRewards.isEmpty();
    }
}
