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

    /**
     * Whether the board may send this townie to downtime. A starving diner must reach the
     * stage-down above instead: downtime's instant-eat restarts the chain at the first diner, so
     * a townie out of cooked food looped dining_no_table -> downtime -> dining_no_table and never
     * tried raw food (2026-10-23 r10, fullness -272% beside a chest of raw beef).
     */
    public static boolean downtimeMayInterrupt(JobID current, boolean starving) {
        return !(starving && isDiner(current));
    }

    /** One of the three diners (table, no table, raw food). */
    public static boolean isDiner(JobID job) {
        return switch (job.jobId()) {
            case DinerWork.ID, DinerNoTableWork.ID, DinerRawFoodWork.ID -> true;
            default -> false;
        };
    }
}
