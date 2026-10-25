package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.jobs.declarative.Preferred;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class JobLogic<EXTRA, TOWN, POS> {

    /**
     * Extra ticks the max-ticks watchdog waits while the job is EXTRACTING_PRODUCT, so the tick a
     * leaver's trip timer completes extracts the trip's product before any hand-off (r16 #3). A
     * window, not an exemption: a job stuck extracting still hands off. Pinned by
     * {@code JobLogicTest.tick_shouldNotHitMaxTicksFallback_OnTheExtractTick}.
     */
    static final long EXTRACT_GRACE_TICKS = 200;

    private int worked = 0;
    private int ticksWithoutNextJob = 0;

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

        /**
         * Whether {@code id} may start now under the job board's day rule (it can always start,
         * or it finishes before evening).
         */
        boolean fitsInDay(JobID id);

        WorkPosition<POS> getWorkSpot();

        boolean tryGrabbingInsertedSupplies();

        boolean tryDropLoot();

        /**
         * The villager holds output of its work (items that are not this job's supplies), which
         * its dropping-loot status carries to storage. A finished cycle waits for that before
         * handing off.
         */
        boolean holdsWorkOutput();

        /** Whether the townie is starving (fullness at zero). */
        boolean isStarving();

        void tryGetSupplies();

        Map<Integer, Collection<WorkPosition<POS>>> listAllWorkSpots();

        void setLookTarget(POS position);

        void registerHeldItemsAsFoundLoot();

        AbstractWorldInteraction<EXTRA, POS, ?, ?, TOWN> getHandle();

        void changeToNextJob();

        boolean setWorkLeftAtFreshState(int workRequiredAtFirstState);

        void clearInsertedSupplies();

        void tryExtractWithNoItem();

        void registerUnmetNeeds(
                ProductionStatus status,
                @Nullable POS workspot,
                int timesInserted
        );

        int timesInserted();

        void registerUnmetRooms();
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
            BiFunction<TOWN, POS, Integer> getState,
            int workRecently

    ) {
        if (!hasAnyItems && computeState.get().isDroppingLoot()) {
            worldBeforeTick.changeToNextJob();
            return;
        }

        if (ticksWithoutNextJob > 500) { // TODO: Config
            QT.JOB_LOGGER.debug(
                    "{} gave up waiting for different work after {} ticks ({})",
                    entityCurrentJob.rootId(),
                    ticksWithoutNextJob,
                    entityCurrentJob.jobId()
            );
            // TODO: Visual indicator that something is wrong
            worldBeforeTick.changeJob(expiration.noSuppliesFallbackFn().apply(entityCurrentJob));
            return;
        }

        this.ticksSinceStart++;
        ProductionStatus status = computeState.get();

        if (ticksSinceStart % 200 == 0 && status.isExtractingProduct()) {
            // This is for handling villagers who get stuck as work seekers
            worldBeforeTick.registerUnmetNeeds(
                    status, Util.orNull(workSpot, WorkPosition::jobBlock),
                    worldBeforeTick.timesInserted()
            );
        }

        if (status.isDroppingLoot() && worldBeforeTick.tryDropLoot()) {
            this.workSpot = null;
            redecideIfStarving(worldBeforeTick, entityCurrentJob);
            return;
        }

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
            // Read before the clear below wipes the record.
            boolean insertedAny = worldBeforeTick.timesInserted() > 0;
            if (worldBeforeTick.tryGrabbingInsertedSupplies()) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
                worldBeforeTick.clearInsertedSupplies();
                // The extract hooks are the job's finish (a diner's meal). Run them once, and only
                // for a cycle that put something in: running them every tick until the job changed
                // fed a diner that gave up ~500 free meals (2026-10-22 fullness log flood).
                if (insertedAny) {
                    worldBeforeTick.tryExtractWithNoItem();
                }
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            worldBeforeTick.changeToNextJob();
            ticksWithoutNextJob++;
            return;
        }

        WorkPosition<POS> workSpot = worldBeforeTick.getWorkSpot();
        // A job waiting on a timer has its cycle in progress (a leaver is away, so it has no
        // work spot): re-creating it then lost the hunt's product (2026-10-17 playtest).
        // The tick a timer completes is EXTRACTING_PRODUCT: a hand-off then left the trip's loot
        // unextracted until the next shift (r16 #3), so extraction gets a grace window.
        long maxTicks = expiration.maxTicks() + (status.isExtractingProduct() ? EXTRACT_GRACE_TICKS : 0);
        if (workSpot == null && !status.isWaitingForTimers() && this.ticksSinceStart > maxTicks) {
            JobID fbJov = expiration.maxTicksFallbackFn()
                                    .apply(entityCurrentJob);
            // A same-job fallback ("keep doing this forever") asks the board: re-creating the job
            // directly skipped the board's BOP-deposit and starving branches, so a chaining
            // hunter never deposited its BOP and hunted on while starving (r12 #8, r14 #2).
            // The board re-rolls the same job when nothing more urgent applies.
            boolean sameJob = fbJov.equals(entityCurrentJob);
            if (sameJob || !worldBeforeTick.fitsInDay(fbJov)) {
                // The fallback skips the board, and with it the day-fit rule: a gatherer was
                // restarted just before evening and left on a trip it slept through (2026-10-22).
                QT.JOB_LOGGER.debug(
                        "Reached max ticks for {}. Fallback {} {}; asking the board.",
                        entityCurrentJob, fbJov, sameJob ? "is the same job" : "won't fit in the day"
                );
                worldBeforeTick.changeToNextJob();
                // If the board keeps this job, wait another window before asking again: the
                // counter never reset, so a stalled hunter asked every tick (2026-10-22 r6).
                this.ticksSinceStart = 0;
                return;
            }
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
            // The state behind the give-up, for r16 #10 (diners took food, then gave up at
            // state:0 without eating; which of spot / site / insert was missing is unknown).
            // r17 #19 adds the spots the job can see, by state: a cook at state:0 with mutton in
            // hand never targeted the kitchen's furnace.
            QT.JOB_LOGGER.debug(
                    "{} gave up waiting for ingredients after {} ticks (job: {}, status: {}, workSpot: {}, inJobSite: {}, inserted: {}, spotsByState: {})",
                    entityCurrentJob.rootId(),
                    noSuppliesTicks,
                    entityCurrentJob.jobId(),
                    status,
                    workSpot,
                    isEntityInJobSite,
                    worldBeforeTick.timesInserted(),
                    worldBeforeTick.listAllWorkSpots()
            );
            this.grabbingInsertedSupplies = true;
            if (status == ProductionStatus.NO_SUPPLIES) {
                worldBeforeTick.registerUnmetNeeds(
                        status,
                        Util.orNull(workSpot, WorkPosition::jobBlock),
                        worldBeforeTick.timesInserted()
                );
            } else if (status == ProductionStatus.NO_JOBSITE) {
                worldBeforeTick.registerUnmetRooms();
            }
            return;
        }

        if (isEntityInJobSite && status.isWorkingOnProduction()) {
            doTryWorking(
                    extra,
                    status.getProductionState(details.maxState()),
                    isSeekingWork,
                    worldBeforeTick,
                    getState,
                    details.workPause,
                    workRecently
            );
        }

        if (status.isDroppingLoot() && worldBeforeTick.tryDropLoot()) {
            this.workSpot = null;
            redecideIfStarving(worldBeforeTick, entityCurrentJob);
            return;
        }

        if (wrappingUp) {
            if (worldBeforeTick.holdsWorkOutput()) {
                // Holding output: carry on as a fresh cycle instead of handing off. The job's own
                // status keeps working while there is work, then drops loot by walking to a chest,
                // and the empty-handed dropping-loot branch above hands off. Handing off here let
                // the board's carried-output deposit teleport the output into the nearest chest
                // (2026-10-20 playtest).
                wrappingUp = false;
                return;
            }
            // TODO: Check if all special rules were leveraged.
            //  If not, spit an error into the console to help with debugging.
            // A finished cycle hands off; it does not fetch supplies for another one. Fetching
            // here fed the board cascade's carried-output deposit a fresh item every tick: a
            // take/return loop of ~300 raw food per meal (2026-10-19, cook-diner-carrot-take-burst).
            worldBeforeTick.changeToNextJob();
            ticksWithoutNextJob++;
            return;
        }
        worldBeforeTick.tryGetSupplies();
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
            int workPause,
            int workedRecently
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
            this.worked = workedRecently + workPause;
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

    /**
     * A deposit is a decision point for a starving townie: the board (which sends it to dine) is
     * otherwise asked only between jobs, and a hunter went straight back out for a whole trip at
     * -104% with food in the chest (r16 #1, dev ruling 2026-10-24). A diner is already going to
     * eat; re-deciding would skip it to the next diner stage.
     */
    private void redecideIfStarving(
            JLWorld<EXTRA, TOWN, POS> world,
            JobID currentJob
    ) {
        if (!world.isStarving() || DiningStages.isDiner(currentJob)) {
            return;
        }
        world.changeToNextJob();
    }

    protected void setWorkSpot(WorkPosition<POS> spot) {
        if (workSpot != null && workSpot.equals(spot)) {
            return;
        }
        QT.JOB_LOGGER.debug("Villager is targeting workspot {}", spot);
        this.workSpot = spot;
    }
}
