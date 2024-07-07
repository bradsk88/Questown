package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;

import java.util.function.Supplier;

public class JobLogic {

    public interface JLWorld<POS> {
        void changeJob(JobID id);

        WorkSpot<Integer, POS> getWorkSpot();

        void clearWorkSpot(String reason);

        boolean tryGrabbingInsertedSupplies();

        boolean tryWorking();

        boolean canDropLoot();

        void tryDropLoot();

        void tryGetSupplies();

        void seekFallbackWork();
    }

    private boolean grabbingInsertedSupplies;
    private boolean grabbedInsertedSupplies;
    private int ticksSinceStart;
    private int noSuppliesTicks;

    public void tick(
            Supplier<ProductionStatus> computeState,
            JobID entityCurrentJob,
            ExpirationRules expiration,
            JLWorld<?> world
    ) {
        if (this.grabbingInsertedSupplies) {
            if (world.tryGrabbingInsertedSupplies()) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            world.seekFallbackWork();
            return;
        }

        this.ticksSinceStart++;
        WorkSpot<Integer, ?> workSpot = world.getWorkSpot();
        if (workSpot == null && this.ticksSinceStart > expiration.maxTicks()) {
            JobID fbJov = expiration.maxTicksFallbackFn()
                                    .apply(entityCurrentJob);
            QT.JOB_LOGGER.debug(
                    "Reached max ticks for {}. Falling back to {}.",
                    entityCurrentJob, fbJov
            );
            world.changeJob(fbJov);
            return;
        }
        world.clearWorkSpot("Cleared before trying work");


        ProductionStatus status = computeState.get();

        if (ProductionStatus.NO_SUPPLIES.equals(status)) {
            noSuppliesTicks++;
        } else {
            noSuppliesTicks = 0;
        }

        if (noSuppliesTicks > expiration.maxTicksWithoutSupplies()) {
            this.grabbingInsertedSupplies = true;
            return;
        }

        if (world.canDropLoot()) {
            world.changeJob(WorkSeekerJob.getIDForRoot(entityCurrentJob));
            return;
        }

        if (world.tryWorking()) {
            return;
        }
        world.tryDropLoot();
        world.tryGetSupplies();
    }
}
