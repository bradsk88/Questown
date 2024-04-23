package ca.bradj.questown.jobs;

public interface TownStateProvider {
    boolean hasSupplies();
    boolean hasSupplies(int i);

    boolean hasSpace();

    boolean canUseMoreSupplies();
    boolean canUseMoreSupplies(int i);

    boolean isTimerActive();

    boolean isCachingAllowed();
}
