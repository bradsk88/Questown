package ca.bradj.questown.gui.town.status;

public final class PendingJobChangeMark {

    private PendingJobChangeMark() {
    }

    /** "Hunter → Cook": the job a villager becomes after its trip. */
    public static String afterTripTitle(String jobTitle, String becomes) {
        return jobTitle + " \u2192 " + becomes;
    }

    public static String afterTripTitleShort(String becomes) {
        return "\u2192 " + becomes;
    }

    public static String title(String jobTitle, boolean jobChangePending) {
        return jobChangePending ? jobTitle + "*" : jobTitle;
    }
}
