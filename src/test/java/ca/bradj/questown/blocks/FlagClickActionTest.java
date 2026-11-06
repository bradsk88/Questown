package ca.bradj.questown.blocks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** What a right-click on the flag does in each phase. */
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
