package ca.bradj.questown.town;

/** When full storage becomes the town's problem: villagers stuck without space add up, and past {@link #BLOCKER_VILLAGER_TICKS} the flag shows the storage bubble. Any free slot resets it. */
public final class StorageBlockTally {

    public static final long BLOCKER_VILLAGER_TICKS = 1200;

    private long villagerTicks = 0;

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
