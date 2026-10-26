package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.gui.StatusArt;
import ca.bradj.questown.jobs.Overrides;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A townie on downtime spends most of it in the job's work states (state:0..8) and its
 * extraction. Those states had no art or text, so the villagers tab showed the error
 * checkerboard and "Gatherer is busy with their work." (agent playtest 2026-10-22 r4).
 */
class DowntimeWorkOverridesTest {

    @Test
    void everyDowntimeWorkState_hasArtAndTheDowntimeText() {
        Overrides o = DowntimeWork.overrides();

        for (int i = 0; i < DowntimeWork.MAX_STATE; i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            Assertions.assertNotNull(o.textureFor(s), "no art for downtime " + s);
            Assertions.assertNotEquals(StatusArt.ERROR_TEX, o.textureFor(s));
            Assertions.assertNotNull(o.statusTextOverrides().get(s), "no text for downtime " + s);
        }
        Assertions.assertNotNull(o.textureFor(ProductionStatus.EXTRACTING_PRODUCT));
        Assertions.assertNotNull(o.statusTextOverrides().get(ProductionStatus.EXTRACTING_PRODUCT));
    }

    @Test
    void noSupplies_readsAsRelaxing() {
        // r22: downtime needs no supplies; the status circuit falls back to NO_SUPPLIES when no
        // wander spot is found, and the tab said "is looking for supplies".
        Overrides o = DowntimeWork.overrides();

        Assertions.assertEquals(
                o.statusTextOverrides().get(ProductionStatus.NO_JOBSITE),
                o.statusTextOverrides().get(ProductionStatus.NO_SUPPLIES)
        );
    }
}
