package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface HeldItem<
        SELF extends Item<SELF>,
        INNER extends Item<INNER>
> extends Item<SELF> {
    boolean isLocked();

    INNER get();

    SELF locked();

    SELF unlocked();

    @Nullable String acquiredViaLootTablePrefix();

    @Nullable String foundInBiome();

    String toShortString();
}
