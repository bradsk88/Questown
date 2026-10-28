package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;

public class UIWork {

    private final WorkRequest resultWanted;
    // Storage already holds enough; villagers make no more until it drops (BoardRequests).
    private final boolean stocked;

    public UIWork(WorkRequest resultWanted, boolean stocked) {
        this.resultWanted = resultWanted;
        this.stocked = stocked;
    }

    public WorkRequest getResultWanted() {
        return resultWanted;
    }

    public boolean isStocked() {
        return stocked;
    }
}
