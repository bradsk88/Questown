package ca.bradj.questown.jobs;

public class GathererStatuses {

    public interface TownStateProvider {
        boolean IsStorageAvailable();
        boolean hasGate();
    }
}
