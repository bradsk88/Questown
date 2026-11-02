package ca.bradj.questown.jobs;

public interface Item<I extends Item<I>> {
    boolean isEmpty();

    boolean isFood();

    /** A Block of Progress stays with the villager through a loot drop; it goes to the flag. */
    default boolean isBlockOfProgress() {
        return false;
    }

    I shrink();

    String getShortName();

     I unit();

     int quantity();
}
