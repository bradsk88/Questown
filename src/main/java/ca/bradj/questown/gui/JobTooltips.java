package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JobTooltips {
    public static ImmutableList<Component> get(
            ProductionStatus status,
            JobID jobId
    ) {
        @Nullable ImmutableList<Component> overrides = ClientAccess.getStatusText(jobId, status);
        return overrides == null ? buildStandardTooltipKeys(status, jobId) : overrides;
    }

    public static @NotNull ImmutableList<Component> buildStandardTooltipKeys(
            IStatus<?> status,
            JobID jobId
    ) {
        // TODO: Render root AND current job
        ImmutableList<Component> components;
        @Nullable String cat = status.getCategoryId();
        if (cat == null) {
            cat = jobId.jobId();
        }

        // TODO: Handle work seeker statuses some where else
        if (WorkSeekerJob.isSeekingWork(jobId)) {
            cat = "work_seeker";
        }
        if (
                DinerNoTableWork.isDining(jobId) ||
                        DinerWork.isDining(jobId) ||
                        DinerRawFoodWork.isDining(jobId)
        ) {
            cat = "diner";
        }
        Component jobName = Compat.translatable("jobs." + jobId.rootId());
        String key1 = String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.nameV2());
        String key2 = String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.nameV2());
        components = ImmutableList.of(
                Compat.translatable(key1, jobName),
                Compat.translatable(key2, jobName)
        );
        return components;
    }

}
