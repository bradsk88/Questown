package ca.bradj.questown.jobs;

public interface TownStateProvider {
    LZCD.Dependency<Void> hasSupplies();

    LZCD.Dependency<Void> hasSpace();

    LZCD.Dependency<Void> isTimerActive();

    LZCD.Dependency<Void> canUseMoreSupplies();
}
