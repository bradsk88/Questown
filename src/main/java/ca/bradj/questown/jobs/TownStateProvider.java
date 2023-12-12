package ca.bradj.questown.jobs;

public interface TownStateProvider {
    boolean hasSupplies();

    boolean hasSpace();

    boolean canUseMoreSupplies();

    boolean isUnfinishedTimeWorkPresent();
}
