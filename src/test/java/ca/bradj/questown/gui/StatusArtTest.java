package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Every status a production job reports (work states {@code state:N}, WAITING_FOR_TIMED_STATE,
 * EXTRACTING_PRODUCT) maps to a texture file that ships with the mod, never the missing-texture
 * checkerboard.
 */
class StatusArtTest {

    private static final JobID GATHERER = new JobID("gatherer", "axe");

    @Test
    void workingWaitingAndExtracting_haveRealArt() {
        List<ProductionStatus> statuses = List.of(
                ProductionStatus.fromJobBlockStatus(0),
                ProductionStatus.fromJobBlockStatus(1),
                ProductionStatus.fromJobBlockStatus(ProductionStatus.firstNonCustomIndex - 1),
                ProductionStatus.WAITING_FOR_TIMED_STATE,
                ProductionStatus.EXTRACTING_PRODUCT
        );
        for (ProductionStatus s : statuses) {
            ResourceLocation tex = StatusArt.getTexture(GATHERER, s);
            Assertions.assertNotEquals(StatusArt.ERROR_TEX, tex, "status " + s + " has no art");
            assertShipped(tex);
        }
    }

    private static void assertShipped(ResourceLocation tex) {
        Path file = Path.of("src/main/resources/assets", tex.getNamespace(), tex.getPath());
        Assertions.assertTrue(Files.isRegularFile(file), "texture file missing: " + file);
    }
}
