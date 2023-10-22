package ca.bradj.questown.jobs;

public interface StatusListener<STATUS> {
    void statusChanged(STATUS newStatus);
}
