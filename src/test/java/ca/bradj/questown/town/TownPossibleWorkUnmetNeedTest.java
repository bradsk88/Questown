package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownPossibleWorkUnmetNeedTest {

    @Test
    void relaxingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(DowntimeWork.getIdFor(new JobID("cook", "mutton")), ProductionStatus.NO_SUPPLIES));
    }

    @Test
    void seekingWork_showsItsUnmetNeed() {
        Assertions.assertTrue(TownPossibleWork.showsUnmetNeed(new JobID("cook", "seeking_work"), ProductionStatus.IDLE));
    }

    @Test
    void workingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(new JobID("cook", "porkchop"), ProductionStatus.GOING_TO_JOB));
    }

    /** A farmer stuck for supplies says so. */
    @Test
    void villagerStuckForSupplies_showsItsUnmetNeed() {
        Assertions.assertTrue(TownPossibleWork.showsUnmetNeed(new JobID("farmer", "harvest_wheat"), ProductionStatus.NO_SUPPLIES));
    }

    @Test
    void eatingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(new JobID("cook", "dining_no_table"), ProductionStatus.NO_SUPPLIES));
    }
}
