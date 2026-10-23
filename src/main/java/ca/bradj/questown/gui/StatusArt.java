package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class StatusArt {

    private static final Map<ProductionStatus, String> pArt;
    // Work states (state:N) are not constants, so they map by predicate in getTexture.
    // working/waiting/extracting.png are placeholders (copies of existing art) until the
    // dev's own icons replace them (2026-10-23 ruling, r9 findings #14).
    private static final String WORKING_ART = "menu/gatherer/working.png";
    private static final String WAITING_ART = "menu/gatherer/waiting.png";
    public static final ResourceLocation ERROR_TEX = new ResourceLocation("questown", "textures/error.png");

    static {
        ImmutableMap.Builder<ProductionStatus, String> b = ImmutableMap.builder();
        b.put(ProductionStatus.IDLE, "menu/gatherer/idle.png");
        b.put(ProductionStatus.NO_SPACE, "menu/gatherer/no_space.png");
        b.put(ProductionStatus.RELAXING, "menu/gatherer/relaxing.png");
        b.put(ProductionStatus.NO_JOBSITE, "menu/gatherer/no_jobsite.png");
        b.put(ProductionStatus.GOING_TO_JOB, "menu/gatherer/leaving.png");
        b.put(ProductionStatus.NO_SUPPLIES, "menu/gatherer/no_supplies.png");
        b.put(ProductionStatus.COLLECTING_SUPPLIES, "menu/gatherer/get_supplies.png");
        b.put(ProductionStatus.NO_WORK_POSSIBLE, "menu/gatherer/no_work_possible.png");
        b.put(ProductionStatus.DROPPING_LOOT, "menu/gatherer/dropping_loot.png");
        b.put(ProductionStatus.WAITING_FOR_TIMED_STATE, WAITING_ART);
        b.put(ProductionStatus.EXTRACTING_PRODUCT, "menu/gatherer/extracting.png");
        pArt = b.build();
    }

    public static ResourceLocation getTexture(
            JobID jobId,
            IStatus<?> status
    ) {
        if (status instanceof ProductionStatus ps) {
            String art = pArt.get(ps);
            if (art == null && ps.isWorkingOnProduction()) {
                art = WORKING_ART;
            }
            if (WorkSeekerJob.isSeekingWork(jobId) && ps.isExtractingProduct()) {
                art = "menu/work_seeker.png";
            }

            if (art != null) {
                return Questown.ResourceLocation( "textures/" + art);
            }
        }

        // TODO: Smelter statuses
        return ERROR_TEX;
    }
}
