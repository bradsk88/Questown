package ca.bradj.questown.town.quests;

/**
 * What a quest batch must add when its completion reward brings a villager: a bedroom quest per bed
 * the town will be short once that villager arrives, and a storage quest (dev ruling 2026-10-24,
 * r16 #11 / #17). The town's bed shortfall counted only townies already in town, so a batch never
 * asked for its own arrival's bed, and nothing ever asked for more chests as the town grew.
 * Pinned by {@code ArrivalProvisionsTest}.
 *
 * @param bedQuests    bedroom quests to add
 * @param storageQuest whether to add a storage room quest
 */
public record ArrivalProvisions(int bedQuests, boolean storageQuest) {

    public static final ArrivalProvisions NONE = new ArrivalProvisions(0, false);

    /**
     * @param missingBedsWithArrival beds the town lacks once the arrival is counted
     * @param bedQuestsInBatch       bed-room quests the batch already carries
     */
    public static ArrivalProvisions plan(
            boolean bringsVillager,
            int missingBedsWithArrival,
            int bedQuestsInBatch,
            boolean batchHasStorageQuest
    ) {
        if (!bringsVillager) {
            return NONE;
        }
        int beds = Math.max(0, missingBedsWithArrival - bedQuestsInBatch);
        return new ArrivalProvisions(beds, !batchHasStorageQuest);
    }
}
