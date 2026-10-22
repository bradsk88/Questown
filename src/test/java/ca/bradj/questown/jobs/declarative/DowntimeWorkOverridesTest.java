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
}
