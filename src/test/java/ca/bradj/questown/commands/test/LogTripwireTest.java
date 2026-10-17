package ca.bradj.questown.commands.test;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
