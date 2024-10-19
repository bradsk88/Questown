package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.Preferred;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class JobLogic<EXTRA, TOWN, POS> {

    private int worked = 0;

    public boolean hasWorkedRecently() {
        return worked > 0;
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

        boolean tryGrabbingInsertedSupplies();

        boolean canDropLoot();

        boolean tryDropLoot();

        void tryGetSupplies();

        void seekFallbackWork();

        Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots();

        void setLookTarget(POS position);

        void registerHeldItemsAsFoundLoot();

        AbstractWorldInteraction<EXTRA, POS, ?, ?, TOWN> getHandle();

        void changeToNextJob();

        boolean setWorkLeftAtFreshState(int workRequiredAtFirstState);

        void clearInsertedSupplies();
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

    private boolean settingUp = true;

    public record JobDetails(
            int maxState,
            @Nullable Integer workRequiredAtFirstState,
            int workPause
    ) {
    }

    public void tick(
            EXTRA extra,
            Supplier<ProductionStatus> computeState,
            JobID entityCurrentJob,
            boolean isEntityInJobSite,
            boolean isSeekingWork,
            boolean hasInsertedAtLeastOneIngredient,
            boolean hasAnyItems,
            ExpirationRules expiration,
            JobDetails details,
            JLWorld<EXTRA, TOWN, POS> worldBeforeTick,
            BiFunction<TOWN, POS, Integer> getState
    ) {
        if (!hasAnyItems && computeState.get().isDroppingLoot()) {
            worldBeforeTick.changeToNextJob();
            return;
        }

        this.ticksSinceStart++;
        ProductionStatus status = computeState.get();

        if (ImmutableList.of(
                ProductionStatus.NO_SUPPLIES,
                ProductionStatus.NO_JOBSITE
        ).contains(status)) {
            noSuppliesTicks++;
        } else if (status.isWorkingOnProduction() && workSpot == null) {
            noSuppliesTicks++;
        } else {
            noSuppliesTicks = 0;
        }

        if (setup(details.workRequiredAtFirstState(), worldBeforeTick)) {
            return;
        }
        worked = Math.max(0, worked - 1);
        if (this.grabbingInsertedSupplies) {
            if (worldBeforeTick.tryGrabbingInsertedSupplies()) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
                worldBeforeTick.clearInsertedSupplies();
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            worldBeforeTick.changeToNextJob();
            return;
        }

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

        long maxNoSupplyTicks = expiration.maxInitialTicksWithoutSuppliesOrJobSite();
        if (hasInsertedAtLeastOneIngredient) {
            maxNoSupplyTicks = expiration.maxTicksWithoutSuppliesOrJobSite();
        }

        if (noSuppliesTicks > maxNoSupplyTicks) {
            QT.JOB_LOGGER.debug(
                    "{} gave up waiting for ingredients after {} ticks ({})",
                    entityCurrentJob.rootId(),
                    noSuppliesTicks,
                    entityCurrentJob.jobId()
            );
            this.grabbingInsertedSupplies = true;
            return;
        }

        if (worldBeforeTick.canDropLoot()) {
            worldBeforeTick.changeToNextJob();
            return;
        }

        if (isEntityInJobSite && status.isWorkingOnProduction()) {
            doTryWorking(
                    extra,
                    status.getProductionState(details.maxState()),
                    isSeekingWork,
                    worldBeforeTick,
                    getState,
                    details.workPause
            );
        }

        if (status.isDroppingLoot() && worldBeforeTick.tryDropLoot()) {
            this.workSpot = null;
        }
        worldBeforeTick.tryGetSupplies();

        if (wrappingUp) {
            // TODO: Check if all special rules were leveraged.
            //  If not, spit an error into the console to help with debugging.
            worldBeforeTick.changeToNextJob();
        }
    }

    protected boolean setup(
            @Nullable Integer workRequiredAtFirstState,
            JLWorld<EXTRA, TOWN, POS> worldBeforeTick
    ) {
        if (!settingUp) {
            return false;
        }
        if (workRequiredAtFirstState != null && workRequiredAtFirstState != 0) {
            if (worldBeforeTick.setWorkLeftAtFreshState(workRequiredAtFirstState)) {
                settingUp = false;
                return true;
            }
        }
        return false;
    }

    // TODO: Phase out this function by extracting "getWorkspots" and "handleWorkOutput"
    private WithReason<Boolean> doTryWorking(
            EXTRA extra,
            int productionState,
            boolean isSeekingWork,
            JLWorld<EXTRA, TOWN, POS> world,
            BiFunction<TOWN, POS, Integer> getState,
            int workPause
    ) {
        // TODO: Update listAllWorkSpots to actually find "rich" workspots (an
        //  implemntation of WorkSpot) which have the block name to help with debugging
        Map<Integer, Collection<WorkPosition<POS>>> workSpots = world.listAllWorkSpots();

        Collection<WorkPosition<POS>> workSpot1 = workSpots.get(productionState);
        if (workSpot1 == null) {
            String problem = "Worker somehow has different status than all existing work spots";
//            QT.JOB_LOGGER.error("{}. This is probably a bug.", problem);
            return WithReason.unformatted(null, problem);
        }
        Preferred<WorkPosition<POS>> allSpots = new Preferred<>(this.workSpot, workSpot1);

        if (allSpots.isEmpty()) {
            return WithReason.unformatted(null, "No workspots");
        }

        WorkOutput<TOWN, WorkPosition<POS>> work = world.getHandle().tryWorking(extra, allSpots);
        this.setWorkSpot(work.spot());
        if (work.worked()) {
            this.worked = Config.WORKED_RECENTLY_TICKS.get().intValue() + workPause;
            world.setLookTarget(work.spot().jobBlock());
            boolean hasWork = !isSeekingWork;
            Integer stateAfterWork = getState.apply(work.town(), work.spot().jobBlock());
            boolean finishedWork = productionState > 0 && stateAfterWork.equals(0);
            if (hasWork && finishedWork) {
                if (!wrappingUp) {
                    world.registerHeldItemsAsFoundLoot(); // TODO: Is this okay for every job to do?
                }
                wrappingUp = true;
            }
        }
        return WithReason.unformatted(wrappingUp, "If worked");
    }

    protected void setWorkSpot(WorkPosition<POS> spot) {
        if (workSpot != null && workSpot.equals(spot)) {
            return;
        }
        QT.JOB_LOGGER.debug("Villager is targeting workspot {}", spot);
        this.workSpot = spot;
    }
}
