package ca.bradj.questown.jobs;

public interface Item<I extends Item<I>> {
    boolean isEmpty();

    boolean isFood();

    /**
     * A Block of Progress belongs on the town flag (dev 2026-10-30: "BOPs should ALWAYS be deposited
     * on the flag"): loot drops leave it with the villager, see {@link HeldItem#staysWithVillager}.
     */
    default boolean isBlockOfProgress() {
        return false;
    }

    I shrink();

    String getShortName();

     I unit();

     int quantity();
}
