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
    /**
     * Last-resort tooltip lines: any category × status key that has no authored entry in
     * en_us.json would otherwise surface to the player as the raw i18n key
     * (2026-10-14 playtest: the diner's tooltip). The specific key wins when present.
     */
    static final String FALLBACK_LINE_1 = "%s is busy with their work.";
    static final String FALLBACK_LINE_2 = "";

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
                Compat.translatableWithFallback(key1, FALLBACK_LINE_1, jobName),
                Compat.translatableWithFallback(key2, FALLBACK_LINE_2, jobName)
        );
        return components;
    }

}
