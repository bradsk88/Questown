package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

public class JobLogic<EXTRA, POS> {

    private boolean worked;

    public boolean hasWorked() {
        return worked;
    }

    public boolean isWrappingUp() {
        return wrappingUp;
    }

    public @Nullable WorkSpot<?, POS> workSpot() {
        return workSpot;
    }

    public interface JLWorld<EXTRA, POS> {
        void changeJob(JobID id);

        WorkSpot<Integer, POS> getWorkSpot();

        void clearWorkSpot(String reason);

        boolean tryGrabbingInsertedSupplies();

        boolean canDropLoot();

        void tryDropLoot();

        void tryGetSupplies();

        void seekFallbackWork();

        Map<Integer, Collection<WorkSpot<Integer, POS>>> listAllWorkSpots();

        void setLookTarget(POS position);

        void registerHeldItemsAsFoundLoot();

        AbstractWorldInteraction<EXTRA, POS, ?, ?, ?> getHandle();
    }

    private WorkSpot<Integer, POS> workSpot;

    public boolean isGrabbingInsertedSupplies() {
        return grabbingInsertedSupplies;
    }

    private boolean grabbingInsertedSupplies;
    private boolean grabbedInsertedSupplies;
    private boolean wrappingUp;
    private int ticksSinceStart;
    private int noSuppliesTicks;

    public void tick(
            EXTRA extra,
            Supplier<ProductionStatus> computeState,
            JobID entityCurrentJob,
            boolean isEntityInJobSite,
            boolean isSeekingWork,
            boolean hasInsertedAtLeastOneIngredient,
            ExpirationRules expiration,
            int maxState,
            JLWorld<EXTRA, POS> world
    ) {
        this.setWorkSpot(world.getWorkSpot());

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

        if (ImmutableList.of(
                ProductionStatus.NO_SUPPLIES,
                ProductionStatus.NO_JOBSITE
        ).contains(status)) {
            noSuppliesTicks++;
        } else {
            noSuppliesTicks = 0;
        }

        long maxNoSupplyTicks = expiration.maxInitialTicksWithoutSupplies();
        if (hasInsertedAtLeastOneIngredient) {
            maxNoSupplyTicks = expiration.maxTicksWithoutSupplies();
        }

        if (noSuppliesTicks > maxNoSupplyTicks) {
            QT.JOB_LOGGER.debug("{} gave up waiting for ingredients after {} ticks ({})", entityCurrentJob.rootId(), noSuppliesTicks, entityCurrentJob.jobId());
            this.grabbingInsertedSupplies = true;
            return;
        }

        if (world.canDropLoot()) {
            world.changeJob(WorkSeekerJob.getIDForRoot(entityCurrentJob));
            return;
        }

        if (isEntityInJobSite && status.isWorkingOnProduction()) {
            doTryWorking(extra, maxState, status.getProductionState(maxState), isSeekingWork, world);
        }

        world.tryDropLoot();
        world.tryGetSupplies();
    }

    // TODO: Phase out this function by extracting "getWorkspots" and "handleWorkOutput"
    private WithReason<Boolean> doTryWorking(
            EXTRA extra,
            int maxState,
            int productionState,
            boolean isSeekingWork,
            JLWorld<EXTRA, POS> world
    ) {
        Map<Integer, Collection<WorkSpot<Integer, POS>>> workSpots = world.listAllWorkSpots(); // TODO: Update listAllWorkSpots to actually find "rich" workspots (an implemntation of WorkSpot) which have the block name to help with debugging

        Collection<WorkSpot<Integer, POS>> allSpots = null;

        Collection<WorkSpot<Integer, POS>> workSpot1 = workSpots.get(productionState);
        if (workSpot1 == null) {
            String problem = "Worker somehow has different status than all existing work spots";
            QT.JOB_LOGGER.error("{}. This is probably a bug.", problem);
            return new WithReason<>(null, problem);
        }
        allSpots = workSpot1;

        if (allSpots.isEmpty()) {
            return new WithReason<>(null, "No workspots");
        }

        // TODO: Pass in the previous workspot and keep working it, if it's sill workable
        WorkOutput<?, WorkSpot<Integer, POS>> work = world.getHandle().tryWorking(extra, allSpots);
        this.setWorkSpot(work.spot());
        if (work.worked()) {
            this.worked = true;
            world.setLookTarget(work.spot().position());
            boolean hasWork = !isSeekingWork;
            boolean finishedWork = work.spot()
                    .action()
                    .equals(maxState); // TODO: Check all workspots before seeking workRequired
            if (hasWork && finishedWork) {
                if (!wrappingUp) {
                    world.registerHeldItemsAsFoundLoot(); // TODO: Is this okay for every job to do?
                }
                wrappingUp = true;
                this.worked = false;
            }
        }
        return new WithReason<>(wrappingUp, "If worked");
    }

    protected void setWorkSpot(WorkSpot<Integer, POS> spot) {
        this.workSpot = spot;
    }
}
