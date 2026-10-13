package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-10-14 playtest ("Diner tooltip is showing i18n key"): the standard status
 * tooltip keys (tooltips.villagers.job.&lt;cat&gt;.status_N.&lt;STATUS&gt;) only exist for
 * authored category × status pairs; anything else (a diner mid-supply-run, a
 * fisher in any status - the fisher has no keys at all) surfaced the raw key
 * to the player. The components must carry a vanilla translatableWithFallback
 * last-resort so a missing key renders the fallback line, never the key.
 */
class JobTooltipsFallbackTest {

    @BeforeAll
    static void bootstrap() {
        // JobTooltips references the diner work classes (Ingredient statics).
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void standardTooltipLinesCarryAFallbackSoMissingKeysNeverRenderRaw() {
        JobID diner = new JobID("gatherer", "dining_no_table");
        ProductionStatus status = ProductionStatus.COLLECTING_SUPPLIES;
        ImmutableList<Component> lines = JobTooltips.buildStandardTooltipKeys(status, diner);

        assertEquals(2, lines.size());
        String s = status.nameV2();
        Component jobName = Compat.translatable("jobs.gatherer");
        assertEquals(
                new TranslatableContents("tooltips.villagers.job.diner.status_1." + s,
                        JobTooltips.FALLBACK_LINE_1, new Object[] { jobName }),
                lines.get(0).getContents(), "line 1");
        assertEquals(
                new TranslatableContents("tooltips.villagers.job.diner.status_2." + s,
                        JobTooltips.FALLBACK_LINE_2, new Object[] { jobName }),
                lines.get(1).getContents(), "line 2");
    }
}
