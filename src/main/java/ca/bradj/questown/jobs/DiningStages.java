package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import org.jetbrains.annotations.Nullable;

/** Which diner a starving villager starts next: one that left a diner still hungry moves down a stage (table, no table, raw food). */
public final class DiningStages {

    private DiningStages() {
    }

    /** The diner to start, or null once raw food has failed too. */
    public static @Nullable JobID next(
            JobID leaving,
            boolean hasDiningRoom
    ) {
        String root = leaving.rootId();
        return switch (leaving.jobId()) {
            case DinerWork.ID -> new JobID(root, DinerNoTableWork.ID);
            case DinerNoTableWork.ID -> new JobID(root, DinerRawFoodWork.ID);
            case DinerRawFoodWork.ID -> null;
            default -> new JobID(root, hasDiningRoom ? DinerWork.ID : DinerNoTableWork.ID);
        };
    }

    /** Whether the board may send this villager to downtime. A starving diner moves down a stage instead. */
    public static boolean downtimeMayInterrupt(JobID current, boolean starving) {
        return !(starving && isDiner(current));
    }

    public static boolean isDiner(JobID job) {
        return switch (job.jobId()) {
            case DinerWork.ID, DinerNoTableWork.ID, DinerRawFoodWork.ID -> true;
            default -> false;
        };
    }
}
