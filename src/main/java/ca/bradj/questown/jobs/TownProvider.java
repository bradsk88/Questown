package ca.bradj.questown.jobs;

public interface TownProvider {
    boolean hasSupplies();
    boolean hasSupplies(int i);

    boolean hasSpace();

    boolean isCachingAllowed();
}
