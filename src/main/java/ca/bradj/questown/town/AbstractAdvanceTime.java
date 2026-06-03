package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.HeldItem;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

/**
 * Abstract time warp implementation that contains no Minecraft dependencies.
 * This class can be used for unit testing without requiring a Minecraft environment.
 *
 * @param <C> Container type
 * @param <I> Item type
 * @param <H> Held item type
 * @param <P> Position type
 * @param <TOWN> Town state type
 * @param <LEVEL> Level/world type (for loot source)
 */
public abstract class AbstractAdvanceTime<
        C extends ContainerTarget.Container<I>,
        I extends Item<I>,
        H extends HeldItem<H, I> & Item<H>,
        P,
        TOWN extends TownState<C, I, H, P, TOWN>,
        LEVEL
        > {

    /**
     * Interface for work-related operations during warp.
     * This allows decoupling from Minecraft-specific job registries.
     */
    public interface Work {
        void recomputeNow();

        @Nullable
        JobID getRandomFinishableWork(
                JobID jobID,
                Signals.DayTime dayTime,
                long ticksElapsed
        );

        long getTotalDuration(
                JobID jobID,
                VillagerUUID vuid
        );

        int getWarpTicksPerCycle(
                JobID jobID,
                VillagerUUID vuid
        );
    }

    /**
     * Factory interface for creating warpers for individual villagers.
     */
    public interface WarperFactory<LEVEL, TOWN extends TownState<?, ?, ?, ?, ?>> {
        Warper<LEVEL, TOWN> createWarper(
                Work work,
                JobID fallbackJobID,
                int villagerIndex
        );
    }

    /**
     * Interface for cooking resolution during warp.
     */
    public interface CookResolver<TOWN, LEVEL> {
        TOWN resolveCooking(TOWN state, LEVEL level, long ticksPerCycle, long availableTicks);
    }

    /**
     * Callback for world-level effects interleaved between
     * villager warp steps. Called once per distinct tick
     * boundary during the warp loop.
     */
    public interface WarpTickCallback<TOWN> {
        TOWN onTick(
                TOWN town, long currentTick, long tickDelta
        );
    }

    /**
     * Interface for logging during warp.
     */
    public interface WarpLogger {
        void log(String message, Object... args);

        void logDetail(String message, Object... args);
    }

    /**
     * Result of the advance time operation.
     */
    public record Result<TOWN>(
            TOWN state,
            long processingTimeMs,
            int totalWarpSteps
    ) {}

    /**
     * Computes important ticks for a villager based on their job.
     * Subclasses implement this to provide tick computation logic.
     */
    protected abstract ImmutableList<Warper.Tick> computeImportantTicks(
            Work work,
            VillagerUUID uuid,
            JobID jobID,
            Predicate<JobID> downtimeCheck,
            long downtimeTicks,
            long ticksPassed,
            long currentTick
    );

    /**
     * Advances time by simulating villager work cycles.
     * <p>
     * The warp carries <em>two</em> timelines (see ADR-0006). Villager labour is scheduled on the
     * {@code ticksPassed} <em>productive</em> budget (work stops at night). World-level passive
     * effects (the {@code warpTickCallback}) run on the {@code wallClockTicks} <em>wall-clock</em> budget so
     * crops, furnaces and trees keep advancing through the night. {@code productiveToWallClock} maps a
     * productive offset (relative to {@code currentTick}) to its wall-clock offset within the window.
     *
     * @param storedState      The town state at the start of the warp
     * @param ticksPassed      Productive ticks to simulate (labour budget)
     * @param wallClockTicks        Wall-clock ticks elapsed (passive-effect budget; {@code >= ticksPassed})
     * @param currentTick      The current game tick (reference point / window end)
     * @param productiveToWallClock Maps a productive offset to its wall-clock offset within the window
     * @param work             Work interface for job resolution
     * @param warperFactory    Factory for creating per-villager warpers
     * @param cookResolver     Optional cooking resolver (can be null)
     * @param warpTickCallback Optional callback for world-level
     *                         effects between warp steps (can be null)
     * @param level            Level/world for loot source
     * @param downtimeCheck    Predicate to check if a job is a downtime job
     * @param downtimeTicks    Number of ticks for downtime period
     * @param logger           Logger for warp operations
     * @return The updated town state after the warp
     */
    public Result<TOWN> advanceTime(
            TOWN storedState,
            long ticksPassed,
            long wallClockTicks,
            long currentTick,
            LongUnaryOperator productiveToWallClock,
            Work work,
            WarperFactory<LEVEL, TOWN> warperFactory,
            @Nullable CookResolver<TOWN, LEVEL> cookResolver,
            @Nullable WarpTickCallback<TOWN> warpTickCallback,
            LEVEL level,
            Predicate<JobID> downtimeCheck,
            long downtimeTicks,
            WarpLogger logger
    ) {
        if (wallClockTicks <= 0) {
            logger.log("Time warp is not applicable (wallClockTicks={})", wallClockTicks);
            return new Result<>(storedState, 0, 0);
        }

        TOWN liveState = storedState;
        List<TownState.VillagerData<H>> villagers = new ArrayList<>(storedState.villagers);

        // Collect all warp steps across all villagers. Labour is scheduled on the productive
        // budget only; an all-night window (ticksPassed <= 0) produces no labour steps but still
        // runs passive effects below.
        final List<Map.Entry<Long, Function<TOWN, TOWN>>> warpSteps = new ArrayList<>();

        for (int i = 0; ticksPassed > 0 && i < villagers.size(); i++) {
            TownState.VillagerData<H> v = villagers.get(i);
            logger.log(
                    "[{}] Warping time by {} ticks, starting with journal: {}",
                    UtilClean.truncateMiddle(v.uuid),
                    ticksPassed,
                    liveState
            );

            ImmutableList<Warper.Tick> ticks = computeImportantTicks(
                    work,
                    VillagerUUID.from(v.uuid),
                    v.journal.jobId(),
                    downtimeCheck,
                    downtimeTicks,
                    ticksPassed,
                    currentTick
            );

            logger.logDetail(
                    "[{}] Computed {} important ticks for job {}",
                    UtilClean.truncateMiddle(v.uuid),
                    ticks.size(),
                    v.journal.jobId()
            );

            int villagerIndex = i;
            Warper<LEVEL, TOWN> vWarper = warperFactory.createWarper(
                    work,
                    v.journal.jobId(),
                    villagerIndex
            );

            // Create warp steps for this villager
            for (Warper.Tick tick : ticks) {
                warpSteps.add(new AbstractMap.SimpleEntry<>(
                        tick.tick(),
                        ts -> vWarper.warp(level, ts, tick.tick(), tick.ticksSincePrevious(), villagerIndex)
                ));
            }
        }

        // Sort all warp steps by tick (chronological order)
        warpSteps.sort(Map.Entry.comparingByKey());
        logger.logDetail(
                "Processing {} total warp steps across {} villagers",
                warpSteps.size(),
                villagers.size()
        );

        // Resolve cooking before processing warp steps
        if (cookResolver != null) {
            liveState = cookResolver.resolveCooking(liveState, level, 0, ticksPassed);
        }

        long before = System.currentTimeMillis();

        // Execute all warp steps, interleaving world-level hooks at tick boundaries.
        // Labour fires on the productive stepTick (lastHookTick); the passive callback fires on
        // the WALL-CLOCK timeline: it receives an absolute wall-clock currentTick (currentTick + wallClockOffset,
        // so plantTick/age references stay coherent) and a wall-clock tickDelta that telescopes to
        // wallClockTicks across the whole window (so night is credited to crops/furnaces).
        long lastHookTick = 0;
        long wallClockCursor = 0;
        for (Map.Entry<Long, Function<TOWN, TOWN>> warpStep
                : warpSteps) {
            long stepTick = warpStep.getKey();
            if (stepTick > lastHookTick
                    && warpTickCallback != null) {
                // Clamp into [0, ticksPassed]: some tick sources (e.g. the downtime skip in
                // ImportantTicks) emit a leading tick that is not currentTick-relative, which
                // would otherwise produce a negative offset (and a crop-shrinking delta).
                long productiveOffset = Math.max(0, Math.min(stepTick - currentTick, ticksPassed));
                long wallClockOffset = Math.max(wallClockCursor, productiveToWallClock.applyAsLong(productiveOffset));
                long wallClockTick = currentTick + wallClockOffset;
                long tickDelta = wallClockOffset - wallClockCursor;
                liveState = warpTickCallback.onTick(
                        liveState, wallClockTick, tickDelta
                );
                lastHookTick = stepTick;
                wallClockCursor = wallClockOffset;
            }
            TOWN affectedState =
                    warpStep.getValue().apply(liveState);
            if (affectedState != null) {
                liveState = affectedState;
            }
        }

        // Final hook call for the remaining wall-clock ticks after the last labour step. This covers
        // the trailing night (and the whole window when an all-night warp produced no steps), so
        // passive effects still run when productive ticks alone would have stopped at dusk.
        if (warpTickCallback != null
                && wallClockTicks > wallClockCursor) {
            long tickDelta = wallClockTicks - wallClockCursor;
            liveState = warpTickCallback.onTick(
                    liveState, currentTick + wallClockTicks, tickDelta
            );
        }

        long after = System.currentTimeMillis();

        logger.log("State after warp of {}: {}", ticksPassed, liveState);
        logger.log("Warp took {} milliseconds", after - before);

        return new Result<>(
                finalizeState(liveState, currentTick),
                after - before,
                warpSteps.size()
        );
    }

    /**
     * Finalizes the state after warp, e.g., updating the world time reference.
     * Subclasses should override to add specific finalization logic.
     */
    protected abstract TOWN finalizeState(TOWN state, long currentTick);
}
