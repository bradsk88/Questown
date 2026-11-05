package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Completes open quests the town already satisfies, like a room quest posted after the room was built. */
final class QuestBackfiller {

    private static final long ROOM_QUEST_WAITING_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static long lastRoomQuestWaitingLog = 0L;

    private QuestBackfiller() {
    }

    static void backfill(
            TownQuests quests,
            TownInterface town,
            Supplier<TownFlagBlockEntity> flag
    ) {
        if (quests.questBatches.isEmpty()) {
            return;
        }
        OpenQuests open = openQuests(quests);
        if (open.levels()) {
            quests.markVillagerLevelsReached(flag.get().highestLevelByRoot());
        }
        completeSatisfiedRoomQuests(quests, town, open.rooms());
        if (open.items()) {
            quests.processItemQuests(TownContainers.getAllStacks(flag.get(), town.getServerLevel()));
        }
    }

    private record OpenQuests(List<MCQuest> rooms, boolean items, boolean levels) {}

    private static OpenQuests openQuests(TownQuests quests) {
        List<MCQuest> rooms = new ArrayList<>();
        boolean items = false;
        boolean levels = false;
        for (MCQuest q : quests.questBatches.getAllBatches().stream().flatMap(QuestBatch::stream).toList()) {
            if (q.isComplete()) {
                continue;
            }
            switch (q.getType()) {
                case ROOM -> rooms.add(q);
                case ITEM -> items = true;
                case VILLAGER_LEVEL -> levels = true;
                default -> { }
            }
        }
        return new OpenQuests(rooms, items, levels);
    }

    private static void completeSatisfiedRoomQuests(
            TownQuests quests,
            TownInterface town,
            List<MCQuest> openRooms
    ) {
        for (MCQuest q : openRooms) {
            Optional<MCRoom> eligible = town.getRoomHandle().getRoomsMatching(q.getWantedId()).stream()
                    .map(RoomRecipeMatch::getRoom)
                    .filter(r -> !quests.isClaimedFor(r, q.getWantedId()))
                    .findFirst();
            if (eligible.isEmpty()) {
                logRoomQuestWaiting(q);
                continue;
            }
            quests.markQuestAsComplete(eligible.get(), q.getWantedId());
        }
    }

    private static void logRoomQuestWaiting(MCQuest q) {
        if (!QT.DEV_LOGS) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastRoomQuestWaitingLog <= ROOM_QUEST_WAITING_LOG_INTERVAL_NANOS) {
            return;
        }
        lastRoomQuestWaitingLog = now;
        QT.QUESTS_LOGGER.debug("Room quest {} is still open: no registered room matches it yet", q.getWantedId());
    }
}
