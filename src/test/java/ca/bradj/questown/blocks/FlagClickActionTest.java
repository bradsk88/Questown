package ca.bradj.questown.blocks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * What a right-click on the town flag does, by lifecycle phase (ADR-0009 loss protection,
 * dev ruling 2026-10-23): a waiting deed is collected first; a dormant flag with no deed
 * waiting offers the way back (wake the town, or get the deed back) instead of doing nothing;
 * every other phase opens the flag menu as before.
 */
class FlagClickActionTest {

    @Test
    void waitingDeed_isCollectedFirst() {
        Assertions.assertEquals(FlagClickAction.COLLECT_DEED, FlagClickAction.of(true, FlagPhase.DORMANT));
    }

    @Test
    void dormantWithNoDeed_opensTheWayBack() {
        Assertions.assertEquals(FlagClickAction.DORMANT_CHOICES, FlagClickAction.of(false, FlagPhase.DORMANT));
    }

    @Test
    void activeAndShuttingDown_openTheFlagMenu() {
        Assertions.assertEquals(FlagClickAction.FLAG_MENU, FlagClickAction.of(false, FlagPhase.ACTIVE));
        Assertions.assertEquals(FlagClickAction.FLAG_MENU, FlagClickAction.of(false, FlagPhase.SHUTTING_DOWN));
    }
}
