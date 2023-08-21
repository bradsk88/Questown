package ca.bradj.questown.jobs;

public interface HeldItem<
        SELF extends GathererJournal.Item<SELF>,
        INNER extends GathererJournal.Item<INNER>
> extends GathererJournal.Item<SELF> {
    boolean isLocked();

    INNER get();

    SELF locked();

    SELF unlocked();
}
