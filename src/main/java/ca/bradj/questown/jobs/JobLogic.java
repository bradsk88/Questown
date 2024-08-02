package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.Preferred;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class JobLogic<EXTRA, TOWN, POS> {

    private boolean worked;

    public boolean hasWorked() {
        return worked;
    }

    public boolean isWrappingUp() {
        return wrappingUp;
    }

    public @Nullable WorkPosition<POS> workSpot() {
        return workSpot;
    }

    public interface JLWorld<EXTRA, TOWN, POS> {
        void changeJob(JobID id);

        WorkPosition<POS> getWorkSpot();

        void clearWorkSpot(String reason);

        boolean tryGrabbingInsertedSupplies();

        boolean canDropLoot();

        void tryDropLoot();

        void tryGetSupplies();

        void seekFallbackWork();

        Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots();

        void setLookTarget(POS position);

        void registerHeldItemsAsFoundLoot();

        AbstractWorldInteraction<EXTRA, POS, ?, ?, TOWN> getHandle();
    }

    private WorkPosition<POS> workSpot;

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
            JLWorld<EXTRA, TOWN, POS> worldBeforeTick,
            BiFunction<TOWN, POS, Integer> getState
    ) {
        if (this.grabbingInsertedSupplies) {
            if (worldBeforeTick.tryGrabbingInsertedSupplies()) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            worldBeforeTick.seekFallbackWork();
            return;
        }

        this.ticksSinceStart++;
        WorkPosition<?> workSpot = worldBeforeTick.getWorkSpot();
        if (workSpot == null && this.ticksSinceStart > expiration.maxTicks()) {
            JobID fbJov = expiration.maxTicksFallbackFn()
                    .apply(entityCurrentJob);
            QT.JOB_LOGGER.debug(
                    "Reached max ticks for {}. Falling back to {}.",
                    entityCurrentJob, fbJov
            );
            worldBeforeTick.changeJob(fbJov);
            return;
        }
//        world.clearWorkSpot("Cleared before trying work");

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

        if (worldBeforeTick.canDropLoot()) {
            worldBeforeTick.changeJob(WorkSeekerJob.getIDForRoot(entityCurrentJob));
            return;
        }

        if (isEntityInJobSite && status.isWorkingOnProduction()) {
            doTryWorking(extra, maxState, status.getProductionState(maxState), isSeekingWork, worldBeforeTick, getState);
        }

        worldBeforeTick.tryDropLoot();
        worldBeforeTick.tryGetSupplies();
    }

    // TODO: Phase out this function by extracting "getWorkspots" and "handleWorkOutput"
    private WithReason<Boolean> doTryWorking(
            EXTRA extra,
            int maxState,
            int productionState,
            boolean isSeekingWork,
            JLWorld<EXTRA, TOWN, POS> world,
            BiFunction<TOWN, POS, Integer> getState
    ) {
        // TODO: Update listAllWorkSpots to actually find "rich" workspots (an
        //  implemntation of WorkSpot) which have the block name to help with debugging
        Map<Integer, Collection<WorkPosition<POS>>> workSpots = world.listAllWorkSpots();

        Collection<WorkPosition<POS>> workSpot1 = workSpots.get(productionState);
        if (workSpot1 == null) {
            String problem = "Worker somehow has different status than all existing work spots";
            QT.JOB_LOGGER.error("{}. This is probably a bug.", problem);
            return WithReason.unformatted(null, problem);
        }
        Preferred<WorkPosition<POS>> allSpots = new Preferred<>(this.workSpot, workSpot1);

        if (allSpots.isEmpty()) {
            return WithReason.unformatted(null, "No workspots");
        }

        WorkOutput<TOWN, WorkPosition<POS>> work = world.getHandle().tryWorking(extra, allSpots);
        this.setWorkSpot(work.spot());
        if (work.worked()) {
            this.worked = true;
            world.setLookTarget(work.spot().jobBlock());
            boolean hasWork = !isSeekingWork;
            Integer stateAfterWork = getState.apply(work.town(), work.spot().jobBlock());
            boolean finishedWork = stateAfterWork.equals(maxState);
            if (hasWork && finishedWork) {
                if (!wrappingUp) {
                    world.registerHeldItemsAsFoundLoot(); // TODO: Is this okay for every job to do?
                }
                wrappingUp = true;
                this.worked = false;
            }
        }
        return WithReason.unformatted(wrappingUp, "If worked");
    }

    protected void setWorkSpot(WorkPosition<POS> spot) {
        this.workSpot = spot;
    }
}
