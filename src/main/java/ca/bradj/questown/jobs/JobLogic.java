package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;

import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class JobLogic<EXTRA, ROOM, POS, BLOCK, WORLD
        extends AbstractWorldInteraction<EXTRA, POS, ?, ?, ?>> {
    private boolean grabbingInsertedSupplies;
    private boolean grabbedInsertedSupplies;
    private int ticksSinceStart;
    private int noSuppliesTicks;

    public void tick(
            EXTRA extra,
            WORLD world,
            Supplier<ProductionStatus> computeState,
            JobID entityCurrentJob,
            ExpirationRules expiration,
            Consumer<JobID> changeJob,
            Supplier<Boolean> tryWorking,
            Supplier<Boolean> canDropLoot,
            Runnable tryDropLoot,
            Runnable tryGetSupplies
    ) {
        if (this.grabbingInsertedSupplies) {
            if (world.tryGrabbingInsertedSupplies(extra)) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            seekFallbackWork(extra);
            return;
        }

        this.ticksSinceStart++;
        WorkSpot<Integer, POS> workSpot = world.getWorkSpot();
        if (workSpot == null && this.ticksSinceStart > expiration.maxTicks()) {
            JobID apply = expiration.maxTicksFallbackFn()
                                    .apply(entityCurrentJob);
            QT.JOB_LOGGER.debug(
                    "Reached max ticks for {}. Falling back to {}.",
                    entityCurrentJob, apply
            );
            changeJob.accept(apply);
            return;
        }
        world.setWorkSpot(new WithReason<>(null, "Cleared before trying work"));


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

        if (canDropLoot.get()) {
            changeJob.accept(WorkSeekerJob.getIDForRoot(entityCurrentJob));
            return;
        }

        if (tryWorking.get()) {
            return;
        }
        tryDropLoot.run();
        tryGetSupplies.run();
    }

    protected abstract void seekFallbackWork(EXTRA extra);
}
