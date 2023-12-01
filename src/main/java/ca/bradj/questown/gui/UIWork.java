package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;

public class UIWork {

    private final WorkRequest resultWanted;

    public UIWork(WorkRequest resultWanted) {
        this.resultWanted = resultWanted;
    }

    public WorkRequest getResultWanted() {
        return resultWanted;
    }
}
