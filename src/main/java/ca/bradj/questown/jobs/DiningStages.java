package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import org.jetbrains.annotations.Nullable;

/**
 * Which diner a starving townie starts from the work board. A townie that leaves a diner and is
 * still starving gave up there without eating (a townie that ate is no longer starving), so it
 * moves one stage down - table, then no table, then raw food - instead of starting the same diner
 * again. Re-entering the same diner was a loop: a town out of villager food had every townie cycle
 * dining_no_table every ~2.5 s with raw meat in storage, and nobody worked (2026-10-22 r5).
 * <p>
 * The diner ids are compile-time constants, so this class does not load the registry-bound
 * diner classes.
 */
public final class DiningStages {

    private DiningStages() {
    }

    /** The diner to start, or null when the townie has just given up on raw food too. */
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

    /** One of the three diners (table, no table, raw food). */
    public static boolean isDiner(JobID job) {
        return switch (job.jobId()) {
            case DinerWork.ID, DinerNoTableWork.ID, DinerRawFoodWork.ID -> true;
            default -> false;
        };
    }
}
