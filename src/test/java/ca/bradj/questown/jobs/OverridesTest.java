package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** One "working" texture covers every work state and extraction. */
class OverridesTest {

    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "textures/block/furnace_front.png");
    private static final ResourceLocation IDLE_ART = new ResourceLocation("questown", "textures/idle.png");

    @Test
    void workingTexture_coversEveryWorkStateAndExtraction() {
        Overrides o = new Overrides(ImmutableMap.of(), ImmutableMap.of(), FURNACE);
        Assertions.assertEquals(FURNACE, o.textureFor(ProductionStatus.fromJobBlockStatus(0)));
        Assertions.assertEquals(FURNACE, o.textureFor(ProductionStatus.fromJobBlockStatus(1)));
        Assertions.assertEquals(FURNACE, o.textureFor(ProductionStatus.FACTORY.extractingProduct()));
    }

    @Test
    void workingTexture_leavesOtherStatusesAlone_andNamedOverridesWin() {
        Overrides o = new Overrides(ImmutableMap.of(ProductionStatus.FACTORY.idle(), IDLE_ART), ImmutableMap.of(), FURNACE);
        Assertions.assertNull(o.textureFor(ProductionStatus.FACTORY.collectingSupplies()));
        Assertions.assertEquals(IDLE_ART, o.textureFor(ProductionStatus.FACTORY.idle()));
        Assertions.assertNull(Overrides.none().textureFor(ProductionStatus.fromJobBlockStatus(0)));
    }
}
