package ca.bradj.questown.town;

/**
 * Whether full storage has become a blocker for the town (2026-10-21 dev request): villagers
 * holding work output with nowhere to put it stop in NO_SPACE (and bubble immediately); once the
 * town has accumulated {@link #BLOCKER_VILLAGER_TICKS} of that across all villagers (one villager
 * for a minute, three for 20 s), the flag shows the storage bubble too. Any free chest slot resets
 * the tally.
 */
public final class StorageBlockTally {

    public static final long BLOCKER_VILLAGER_TICKS = 1200;

    private long villagerTicks = 0;

    /**
     * @param blockedVillagers villagers currently stopped for lack of storage
     * @param townHasSpace     whether any town container has a free slot
     * @param ticks            ticks since the previous observation
     */
    public void observe(int blockedVillagers, boolean townHasSpace, long ticks) {
        if (townHasSpace || blockedVillagers <= 0) {
            villagerTicks = 0;
            return;
        }
        villagerTicks += (long) blockedVillagers * ticks;
    }

    public boolean isBlocker() {
        return villagerTicks >= BLOCKER_VILLAGER_TICKS;
    }
}
