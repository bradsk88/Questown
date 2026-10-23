package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * r9 findings #4 (dev ruling 2026-10-23, "row + status line"): a swordless hunter's status said
 * "Hunter is looking for supplies. They will search in room chests." and its needs row showed a
 * Job Board; nothing named the sword the town knows it is blocked on. While a villager is not
 * working, its unmet item replaces the generic second line and the needs row.
 */
class StatusNeedLineTest {

    @Test
    void notWorking_needReplacesTheSecondLine() {
        Assertions.assertEquals(
                List.of("Hunter is looking for supplies.", "They need Swords to work."),
                StatusNeedLine.lines(
                        List.of("Hunter is looking for supplies.", "They will search in room chests."),
                        "They need Swords to work.", false)
        );
    }

    @Test
    void notWorking_singleLineGetsTheNeedAppended() {
        Assertions.assertEquals(
                List.of("Hunter is relaxing.", "They need Swords to work."),
                StatusNeedLine.lines(List.of("Hunter is relaxing."), "They need Swords to work.", false)
        );
        Assertions.assertEquals(
                List.of("They need Swords to work."),
                StatusNeedLine.lines(List.of(), "They need Swords to work.", false)
        );
    }

    @Test
    void working_orNoNeed_leavesTheLinesAlone() {
        List<String> base = List.of("Cook is working.", "Line two.");
        Assertions.assertEquals(base, StatusNeedLine.lines(base, "They need Coal to work.", true));
        Assertions.assertEquals(base, StatusNeedLine.lines(base, null, false));
    }

    @Test
    void needsRow_showsTheUnmetItemOnlyWhenNotWorking() {
        Assertions.assertEquals(List.of("sword"), StatusNeedLine.neededItems(List.of("job_board"), "sword", false));
        Assertions.assertEquals(List.of("beef"), StatusNeedLine.neededItems(List.of("beef"), "coal", true));
        Assertions.assertEquals(List.of("job_board"), StatusNeedLine.neededItems(List.of("job_board"), null, false));
    }
}
