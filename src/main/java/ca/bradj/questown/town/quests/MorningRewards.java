package ca.bradj.questown.town.quests;

import java.util.Collection;

/**
 * Pure, registry-free rules for the morning-reward queue.
 *
 * <p>Extracted from {@link MCMorningRewards} so the "is anything queued?" decision
 * can be unit-tested without constructing {@code MCMorningRewards} (whose constructor
 * resolves {@code RewardsInit.LIST}, which cannot initialize in a headless JVM).
 */
public final class MorningRewards {

    private MorningRewards() {
    }

    /**
     * A morning reward is pending iff the queue holds any reward. This is the
     * generic "any reward queued" query — deliberately not type-specific like
     * {@code hasPendingSpawnVisitor}, which only counts queued villager spawns.
     */
    public static boolean isAnyPending(Collection<?> queuedRewards) {
        return !queuedRewards.isEmpty();
    }
}
