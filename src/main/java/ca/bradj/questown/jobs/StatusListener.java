package ca.bradj.questown.jobs;

public interface StatusListener {
    void statusChanged(GathererJournal.Status newStatus);
}
