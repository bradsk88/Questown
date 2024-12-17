package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JobTooltips {
    public static ImmutableList<Component> get(
            IStatus<?> status,
            JobID jobId
    ) {
        UtilClean.Pair<String, String> overrides = JobsRegistry.getStatusText(jobId, status);
        if (overrides != null) {
            return UtilClean.Pair.toList(UtilClean.Pair.monoMap(overrides, TranslatableComponent::new));
        }
        return buildStandardTooltipComponents(status, jobId);
    }

    private static @NotNull ImmutableList<Component> buildStandardTooltipComponents(
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
        String key1 = String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.nameV2());
        String key2 = String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.nameV2());
        components = ImmutableList.of(
                new TranslatableComponent(key1, cat, status.nameV2()),
                new TranslatableComponent(key2, cat, status.nameV2())
        );
        return components;
    }

}
