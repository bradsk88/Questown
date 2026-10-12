package ca.bradj.questown.town.quests;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public abstract class AbstractQuestGarden<BATCH, ROOM_ID> {

    private final int idealTicks;
    private final int maxTicks;

    public int getCostSoFar() {
        return costSoFar;
    }

    private int costSoFar;
    private int ticksSoFar;
    private int maxCost;

    protected AbstractQuestGarden(
            int idealTicks,
            int maxTicks,
            int maxCost
    ) {
        this.batch = getEmptyBatch();
        this.idealTicks = idealTicks;
        this.maxTicks = maxTicks;
        this.maxCost = maxCost;
    }

    protected abstract BATCH getEmptyBatch();

    protected abstract ROOM_ID getRandomRoom(Collection<ROOM_ID> rooms);

    BATCH batch;

    /**
     * @return true if growth occurred
     */
    public boolean grow(
            Supplier<Integer> missingBeds,
            Supplier<Collection<RoomNeed<ROOM_ID>>> neededRooms,
            Supplier<Collection<ROOM_ID>> allRecipes
    ) {
        if (costSoFar >= maxCost) {
            return false;
        }
        this.ticksSoFar++;

        if (isOvergrown()) {
            return false;
        }

        boolean added = doGrow(missingBeds, neededRooms, allRecipes);
        if (this.ticksSoFar > maxTicks) {
            return false;
        }
        return added;
    }

    protected abstract boolean isOvergrown();

    private boolean doGrow(
            Supplier<Integer> missingBeds,
            Supplier<Collection<RoomNeed<ROOM_ID>>> neededRooms,
            Supplier<Collection<ROOM_ID>> allRecipes
    ) {
        // The bed shortfall is covered one bedroom quest per grow tick: while the batch holds
        // fewer bed quests than the shortfall, post a bed quest; once it covers the shortfall
        // (or there is none), post room quests. This lets a batch carry more than one bed
        // quest when several beds are missing (day-1 playtest: the town was under-bedded
        // because it posted at most one bed quest per batch).
        int need = missingBeds.get();
        if (need <= 0 || bedQuestCount(batch) >= need) {
            Collection<RoomNeed<ROOM_ID>> nr = neededRooms.get();
            // A batch carries unique requests: a needed recipe already in the
            // batch waits for a later batch instead of filling this one (the
            // run-16 kitchen x3 batch - the room-need queue re-picks the same
            // recipe every grow tick, so without this filter one recipe could
            // occupy the whole batch).
            List<ROOM_ID> fresh = nr.stream()
                                    .map(RoomNeed::id)
                                    .filter(id -> !questAlreadyRequested(batch, id))
                                    .toList();
            if (fresh.isEmpty()) {
                ROOM_ID randomRoom = getRandomRoom(allRecipes.get());
                if (shouldSkip(randomRoom)) {
                    return true;
                }
                addAndRecord(randomRoom);
                return true;
            }

            if (fresh.size() == 1) {
                addAndRecord(fresh.get(0));
                return true;
            }
            addAndRecord(getRandomRoom(fresh));
            return true;
        }
        // TODO: Can we pre-compute the next villager's name?
        //  (or get the UUID of a villager who has no bed?)
        //  This would allow us to have villager-tied "bedroom upgrade" quests
        addBedQuest(null, batch);
        costSoFar += getBedCost();
        return true;
    }

    private void addAndRecord(ROOM_ID randomRoom) {
        addQuest(batch, randomRoom);
        int cost = getCost(randomRoom);
        if (notValid(cost)) {
            return;
        }
        costSoFar += cost;
    }

    /** Number of bedroom quests already in the batch (used to cover the bed shortfall). */
    protected abstract int bedQuestCount(BATCH batch);

    private boolean shouldSkip(
            ROOM_ID id
    ) {
        int newCost = getCost(id);
        if (notValid(newCost)) {
            return true;
        }
        if (questAlreadyRequested(batch, id)) {
            // unique-per-batch: the repeat is priced out of existence - it
            // waits for the next batch rather than diluting this one
            return true;
        }
        if (ticksSoFar < idealTicks && (newCost < maxCost / 4f)) {
            // Skip basic rooms early on
            return true;
        }
        if ((newCost > maxCost / 2)) {
            // Skip complex rooms
            return true;
        }
        if (newCost > maxCost - costSoFar) {
            return true;
        }
        return false;
    }

    private static boolean notValid(int newCost) {
        return Integer.MAX_VALUE == newCost || newCost < 0;
    }

    protected abstract boolean questAlreadyRequested(
            BATCH batch,
            ROOM_ID id
    );

    protected abstract int getBedCost();

    protected abstract int getCost(ROOM_ID randomRoom);

    protected abstract void addQuest(
            BATCH batch,
            ROOM_ID next
    );

    protected abstract void addBedQuest(
            UUID ownerUUID,
            BATCH batch
    );

    public BATCH get() {
        return batch;
    }
}
