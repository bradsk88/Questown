package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.gui.StatusArt;
import ca.bradj.questown.jobs.Overrides;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Every downtime work state and extraction has its own art and text. */
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

    /** Relaxing for a moment shows the idle art, not the night scene. */
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
        // The fallback with no wander spot reads as relaxing too.
        Overrides o = DowntimeWork.overrides();

        Assertions.assertEquals(
                o.statusTextOverrides().get(ProductionStatus.NO_JOBSITE),
                o.statusTextOverrides().get(ProductionStatus.NO_SUPPLIES)
        );
    }
}
