package ca.bradj.questown.jobs;

public interface StatusListener {
    void statusChanged(IStatus<?> newStatus);
}
