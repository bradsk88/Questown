package ca.bradj.questown.town.quests;

/**
 * What a batch adds when its reward brings a villager: a bedroom quest per missing bed, and a
 * storage quest.
 *
 * @param bedQuests    bedroom quests to add
 * @param storageQuest whether to add a storage room quest
 */
public record ArrivalProvisions(int bedQuests, boolean storageQuest) {

    public static final ArrivalProvisions NONE = new ArrivalProvisions(0, false);

    /**
     * @param missingBedsWithArrival beds the town lacks once the villager arrives
     * @param bedQuestsInBatch       bedroom quests the batch already has
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
