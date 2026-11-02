package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;

public class UIWork {

    private final WorkRequest resultWanted;
    // Storage holds enough; villagers stop until it drops.
    private final boolean stocked;
    // How much storage holds, and the limit at which the request pauses.
    private final int stock;
    private final int limit;

    public UIWork(WorkRequest resultWanted, boolean stocked, int stock, int limit) {
        this.resultWanted = resultWanted;
        this.stocked = stocked;
        this.stock = stock;
        this.limit = limit;
    }

    public WorkRequest getResultWanted() {
        return resultWanted;
    }

    public boolean isStocked() {
        return stocked;
    }

    public int getStock() {
        return stock;
    }

    public int getLimit() {
        return limit;
    }
}
