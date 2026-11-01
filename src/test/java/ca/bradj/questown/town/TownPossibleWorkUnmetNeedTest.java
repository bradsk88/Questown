package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TownPossibleWorkUnmetNeedTest {

    /** A relaxing villager shows no need bubble. */
    @Test
    void relaxingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(DowntimeWork.getIdFor(new JobID("cook", "mutton")), ProductionStatus.NO_SUPPLIES));
    }

    @Test
    void seekingWork_showsItsUnmetNeed() {
        Assertions.assertTrue(TownPossibleWork.showsUnmetNeed(new JobID("cook", "seeking_work"), ProductionStatus.IDLE));
    }

    /** A working villager (a cook carrying pork to the kitchen) shows no need bubble. */
    @Test
    void workingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(new JobID("cook", "porkchop"), ProductionStatus.GOING_TO_JOB));
    }

    /** autotest farmer/harvest_wheat [no_supplies]: a farmer stuck in its job for supplies says so. */
    @Test
    void villagerStuckForSupplies_showsItsUnmetNeed() {
        Assertions.assertTrue(TownPossibleWork.showsUnmetNeed(new JobID("farmer", "harvest_wheat"), ProductionStatus.NO_SUPPLIES));
    }

    @Test
    void eatingVillager_showsNoUnmetNeed() {
        Assertions.assertFalse(TownPossibleWork.showsUnmetNeed(new JobID("cook", "dining_no_table"), ProductionStatus.NO_SUPPLIES));
    }
}
