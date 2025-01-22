package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.Config;

import java.util.Collection;
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
            Supplier<Boolean> hasEnoughBeds,
            Supplier<Collection<RoomNeed<ROOM_ID>>> neededRooms,
            Supplier<Collection<ROOM_ID>> allRecipes
    ) {
        if (costSoFar >= maxCost) {
            return false;
        }
        this.ticksSoFar++;
        boolean added = doGrow(hasEnoughBeds, neededRooms, allRecipes);
        if (this.ticksSoFar > maxTicks) {
            return false;
        }
        return added;
    }

    private boolean doGrow(
            Supplier<Boolean> hasEnoughBeds,
            Supplier<Collection<RoomNeed<ROOM_ID>>> neededRooms,
            Supplier<Collection<ROOM_ID>> allRecipes
    ) {
        if (hasEnoughBeds.get()) {
            Collection<RoomNeed<ROOM_ID>> nr = neededRooms.get();
            if (nr.isEmpty()) {
                ROOM_ID randomRoom = getRandomRoom(allRecipes.get());
                if (shouldSkip(randomRoom)) {
                    return true;
                }
                addQuest(batch, randomRoom);
                costSoFar += getCost(randomRoom);
                return true;
            }

            if (nr.size() == 1) {
                ROOM_ID id = nr.iterator().next().id();
                addQuest(batch, id);
                costSoFar += getCost(id);
                return true;
            }
            ROOM_ID randomNeededRoom = getRandomRoom(nr.stream().map(RoomNeed::id).toList());
            addQuest(batch, randomNeededRoom);
            costSoFar += getCost(randomNeededRoom);
            return true;
        }
        // TODO: Can we pre-compute the next villager's name?
        //  (or get the UUID of a villager who has no bed?)
        //  This would allow us to have villager-tied "bedroom upgrade" quests
        addBedQuest(null, batch);
        costSoFar += getBedCost();
        return true;
    }

    private boolean shouldSkip(
            ROOM_ID id
    ) {
        int newCost = getCost(id);
        if (questAlreadyRequested(batch, id)) {
            newCost = (int) (newCost * Config.DUPLICATE_QUEST_COST_FACTOR.get());
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
