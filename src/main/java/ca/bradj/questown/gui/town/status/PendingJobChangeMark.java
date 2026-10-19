package ca.bradj.questown.gui.town.status;

/** The flag villagers page marks a job title whose villager changes job next morning. */
public final class PendingJobChangeMark {

    private PendingJobChangeMark() {
    }

    public static String title(String jobTitle, boolean jobChangePending) {
        return jobChangePending ? jobTitle + "*" : jobTitle;
    }
}
