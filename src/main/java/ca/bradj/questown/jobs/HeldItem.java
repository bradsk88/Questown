package ca.bradj.questown.jobs;

public interface HeldItem<
        SELF extends Item<SELF>,
        INNER extends Item<INNER>
> extends Item<SELF> {
    boolean isLocked();

    INNER get();

    SELF locked();

    SELF unlocked();
}
