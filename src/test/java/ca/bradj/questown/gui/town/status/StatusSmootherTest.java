package ca.bradj.questown.gui.town.status;

import ca.bradj.questown.gui.StatusPacket;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The villagers page smooths each card's status over the last few frames. 2026-10-21 playtest:
 * with the page left open, the leaver-return bar stayed full after the hunter's next trip began,
 * and toggling tabs (a fresh screen) fixed it.
 */
class StatusSmootherTest {

    private static final UUID HUNTER = UUID.randomUUID();
    private static final JobID JOB = new JobID("hunter", "sword");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static StatusPacket status(String image, long returnTick) {
        return new StatusPacket(
                JOB, ImmutableList.of(), new ResourceLocation("questown", image), returnTick, 600
        );
    }

    private static StatusPacket frames(StatusSmoother s, StatusPacket p, int n) {
        StatusPacket shown = null;
        for (int i = 0; i < n; i++) {
            shown = s.smooth(HUNTER, p);
        }
        return shown;
    }

    @Test
    void nextTrip_showsTheNewReturnTick_afterALongOpenScreen() {
        StatusSmoother s = new StatusSmoother();
        frames(s, status("away", 1000), 2000);
        frames(s, status("dropping_loot", 0), 3);
        StatusPacket shown = frames(s, status("away", 5000), 5);
        assertEquals(5000, shown.leaverReturnTick());
    }

    @Test
    void newStatus_shows_afterALongOpenScreen() {
        StatusSmoother s = new StatusSmoother();
        frames(s, status("away", 1000), 2000);
        StatusPacket shown = frames(s, status("dropping_loot", 0), 3);
        assertEquals("dropping_loot", shown.image().getPath());
    }

    @Test
    void oneFrameFlicker_isSmoothedAway() {
        StatusSmoother s = new StatusSmoother();
        frames(s, status("away", 1000), 10);
        StatusPacket shown = s.smooth(HUNTER, status("dropping_loot", 0));
        assertEquals("away", shown.image().getPath());
    }
}
