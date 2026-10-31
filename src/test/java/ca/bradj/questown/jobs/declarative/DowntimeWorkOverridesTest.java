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

    /**
     * Dev playtest 2026-10-28: "Relaxing for a moment is showing the \"night\" status icon. it should
     * be using idle." e8a63f0e picked the RELAXING status art for the word "relaxing", but RELAXING
     * is the end-of-day status and its art is the night scene.
     */
    @Test
    void everyDowntimeStatus_showsTheIdleArt() {
        Overrides o = DowntimeWork.overrides();
        Object idle = StatusArt.getTexture(DowntimeWork.getIdForRoot("___"), ProductionStatus.IDLE);

        for (int i = 0; i < DowntimeWork.MAX_STATE; i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            Assertions.assertEquals(idle, o.textureFor(s), "downtime " + s);
        }
        Assertions.assertEquals(idle, o.textureFor(ProductionStatus.EXTRACTING_PRODUCT));
        Assertions.assertEquals(idle, o.textureFor(ProductionStatus.NO_JOBSITE));
        Assertions.assertEquals(idle, o.textureFor(ProductionStatus.NO_SUPPLIES));
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
