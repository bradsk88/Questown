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

    static {
        ImmutableMap.Builder<ProductionStatus, String> b = ImmutableMap.builder();
        b.put(ProductionStatus.IDLE, "menu/gatherer/idle.png");
        b.put(ProductionStatus.NO_SPACE, "menu/gatherer/no_space.png");
        b.put(ProductionStatus.RELAXING, "menu/gatherer/relaxing.png");
        b.put(ProductionStatus.NO_JOBSITE, "menu/gatherer/no_jobsite.png");
        b.put(ProductionStatus.GOING_TO_JOB, "menu/gatherer/leaving.png");
        b.put(ProductionStatus.NO_SUPPLIES, "menu/gatherer/no_supplies.png");
        b.put(ProductionStatus.COLLECTING_SUPPLIES, "menu/gatherer/get_supplies.png");
        pArt = b.build();
    }

    public static ResourceLocation getTexture(
            JobID jobId,
            IStatus<?> status) {
        if (status instanceof ProductionStatus ps) {
            String art = pArt.get(ps);
            if (WorkSeekerJob.isSeekingWork(jobId) && ps.isExtractingProduct()) {
                art = "menu/work_seeker.png";
            }

            if (art != null) {
                return Questown.ResourceLocation( "textures/" + art);
            }
        }

        // TODO: Smelter statuses
        return new ResourceLocation("questown", "textures/error.png");
    }
}
