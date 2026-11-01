package ca.bradj.questown.gui.town.status;

/** The flag villagers page marks a job title whose villager changes job next morning. */
public final class PendingJobChangeMark {

    private PendingJobChangeMark() {
    }

    /**
     * A switch the player queued for after the villager's trip: the card's job
     * title names the job it becomes, "Hunter \u2192 Cook", until the switch happens.
     */
    public static String afterTripTitle(String jobTitle, String becomes) {
        return jobTitle + " \u2192 " + becomes;
    }

    /** {@link #afterTripTitle} without the current job, for a card too narrow for both. */
    public static String afterTripTitleShort(String becomes) {
        return "\u2192 " + becomes;
    }

    public static String title(String jobTitle, boolean jobChangePending) {
        return jobChangePending ? jobTitle + "*" : jobTitle;
    }
}
