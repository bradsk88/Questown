package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The NO_STORAGE need rule the townie runtime applies each tick (moved off the entity with the
 * rest of the townie's decisions): full storage bubbles at once, beats UNMET_ITEM, and clears only
 * itself. The runtime's tick that calls it needs a live job and is covered by the autotest suite.
 */
class TownieRuntimeStorageNeedTest {

    @Test
    void Test_NoSpaceRaisesNoStorageFromNone() {
        Assertions.assertEquals(
                TownieNeed.NO_STORAGE,
                TownieRuntime.storageNeedAfter(TownieNeed.NONE, ProductionStatus.NO_SPACE)
        );
    }

    @Test
    void Test_NoSpaceReplacesUnmetItem() {
        Assertions.assertEquals(
                TownieNeed.NO_STORAGE,
                TownieRuntime.storageNeedAfter(TownieNeed.UNMET_ITEM, ProductionStatus.NO_SPACE)
        );
    }

    @Test
    void Test_NoSpaceLeavesCantReachAlone() {
        Assertions.assertEquals(
                TownieNeed.CANT_REACH,
                TownieRuntime.storageNeedAfter(TownieNeed.CANT_REACH, ProductionStatus.NO_SPACE)
        );
    }

    @Test
    void Test_LeavingNoSpaceClearsNoStorage() {
        Assertions.assertEquals(
                TownieNeed.NONE,
                TownieRuntime.storageNeedAfter(TownieNeed.NO_STORAGE, ProductionStatus.IDLE)
        );
    }

    @Test
    void Test_LeavingNoSpaceKeepsOtherNeeds() {
        Assertions.assertEquals(
                TownieNeed.UNMET_ITEM,
                TownieRuntime.storageNeedAfter(TownieNeed.UNMET_ITEM, ProductionStatus.IDLE)
        );
    }

    @Test
    void Test_NullStatusIsNotBlocked() {
        Assertions.assertEquals(
                TownieNeed.NONE,
                TownieRuntime.storageNeedAfter(TownieNeed.NO_STORAGE, null)
        );
    }
}
