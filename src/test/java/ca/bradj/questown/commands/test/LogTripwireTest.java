package ca.bradj.questown.commands.test;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTripwireTest {

    @Test
    void stuckBlockReseedWarn_trips() {
        assertEquals(LogTripwire.Kind.STUCK_RESEED, LogTripwire.classify(
                Level.WARN, "Questown:Flag",
                "Work block at BlockPos{x=0, y=64, z=0} stuck in state [state=2] for 20000 ticks with no timer; reseeding to fresh"
        ));
    }

    @Test
    void questownError_trips() {
        assertEquals(LogTripwire.Kind.ERROR, LogTripwire.classify(
                Level.ERROR, "ca.bradj.questown.QT", "In slot 5, expected carrot but state had air."
        ));
    }

    @Test
    void ordinaryQuestownWarn_doesNotTrip() {
        assertEquals(LogTripwire.Kind.NONE, LogTripwire.classify(
                Level.WARN, "Questown:Jobs", "Trying to drop too quickly"
        ));
    }

    @Test
    void vanillaError_doesNotTrip() {
        assertEquals(LogTripwire.Kind.NONE, LogTripwire.classify(
                Level.ERROR, "net.minecraft.server.network.ServerConnectionListener", "Connection reset"
        ));
    }

    @Test
    void sentQuestAnnouncement_isAQuestAnnouncement() {
        assertEquals(LogTripwire.Kind.QUEST_ANNOUNCEMENT, LogTripwire.classify(
                Level.INFO, "Questown:Flag",
                "Broadcasting message: New quests are available! Right-click the town flag to see them. []"
        ));
    }

    @Test
    void queuedQuestAnnouncement_isAQuestAnnouncement() {
        assertEquals(LogTripwire.Kind.QUEST_ANNOUNCEMENT, LogTripwire.classify(
                Level.INFO, "Questown:Flag",
                "Queued broadcast (9 ticks until next slot): New quest is available: post your first work request on the job board. []"
        ));
    }

    @Test
    void otherTownMessage_doesNotTrip() {
        assertEquals(LogTripwire.Kind.NONE, LogTripwire.classify(
                Level.INFO, "Questown:Flag",
                "Queued broadcast (4 ticks until next slot): messages.town_flag.quest_completed [translation{key='room.bedroom', args=[]}]"
        ));
    }

    @Test
    void droppedDuplicate_isNotABroadcast() {
        assertEquals(LogTripwire.Kind.NONE, LogTripwire.classify(
                Level.INFO, "Questown:Flag",
                "Dropped duplicate queued broadcast: New quests are available! Right-click the town flag to see them. []"
        ));
    }

    @Test
    void ordinaryQuestownInfo_doesNotTrip() {
        assertEquals(LogTripwire.Kind.NONE, LogTripwire.classify(
                Level.INFO, "Questown:Villagers", "Job changed to JobID{rootId='hunter', jobId='sword'}"
        ));
    }

    @Test
    void announcementsInOneSlot_areOneBurst() {
        assertTrue(LogTripwire.sameBurst(1000, 1000));
        assertTrue(LogTripwire.sameBurst(1000, 1000 + LogTripwire.ANNOUNCEMENT_BURST_TICKS - 1));
    }

    @Test
    void announcementsASlotApart_areSeparate() {
        assertFalse(LogTripwire.sameBurst(1000, 1000 + LogTripwire.ANNOUNCEMENT_BURST_TICKS));
    }

    @Test
    void firstAnnouncement_isNoBurst() {
        assertFalse(LogTripwire.sameBurst(Long.MIN_VALUE, 0));
    }
}
