package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface HeldItem<
        SELF extends Item<SELF>,
        INNER extends Item<INNER>
> extends Item<SELF> {
    boolean isLocked();

    /** Kept through a loot drop: a player-locked slot, or a Block of Progress bound for the flag. */
    default boolean staysWithVillager() {
        return isLocked() || isBlockOfProgress();
    }

    INNER get();

    SELF locked();

    SELF unlocked();

    @Nullable String acquiredViaLootTablePrefix();

    @Nullable String foundInBiome();

    String toShortString();
}
