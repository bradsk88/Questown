package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownPossibleWorkUnmetNeedTest {

    /** Dev playtest 2026-10-29: "Relaxing villager shouldn't show a need bubble". */
    @Test
    void relaxingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(DowntimeWork.getIdFor(new JobID("cook", "mutton"))));
    }

    @Test
    void seekingWork_showsItsUnmetNeed() {
        Assertions.assertTrue(TownPossibleWork.showsUnmetNeed(new JobID("cook", "seeking_work")));
    }
}
