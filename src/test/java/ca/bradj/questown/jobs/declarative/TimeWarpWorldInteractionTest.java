package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tests for time warp interval behavior.
 * <p>
 * These tests verify that the injectTicks mechanism properly handles the case where
 * consecutive warp ticks have small ticksPassed values but the job has a larger interval.
 * <p>
 * The bug (fixed in commit 28ad8a54):
 * - During warp, each tick creates a new TimeWarpWorldInteraction with ticksSinceLastAction=0
 * - If ticksPassed (e.g., 1) is less than interval (e.g., 50), work would be skipped
 * - The fix ensures ticksSinceLastAction is set to at least interval so work always proceeds during warp
 * <p>
 * This class contains two types of tests:
 * 1. Tests that verify the actual TimeWarpWorldInteraction.injectTicks() behavior
 * 2. Integration tests using IntervalAwareTestWorldInteraction
 */
class TimeWarpWorldInteractionTest {

    // ===================================================================================
    // PART 1: Tests for actual TimeWarpWorldInteraction.injectTicks() behavior
    // These tests will FAIL on the buggy code and PASS when the fix is applied
    // ===================================================================================

    /**
     * Creates a minimal TimeWarpWorldInteraction for testing injectTicks().
     * Uses null/minimal values where possible since we're only testing the tick injection logic.
     */
    private TimeWarpWorldInteraction createMinimalTimeWarpWorldInteraction(int interval) {
        // Create minimal DeclarativeJobChecks with proper types
        DeclarativeJobChecks<TimeWarpWorldInteraction.Inputs, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks =
                new DeclarativeJobChecks<>(
                        ImmutableMap.of(), // no ingredients
                        ImmutableMap.of(), // no ingredient quantities
                        ImmutableMap.of(), // no tools
                        ImmutableMap.of(), // no work
                        ImmutableMap.of(), // no time
                        room -> true,
                        block -> true
                );

        return new TimeWarpWorldInteraction(
                BlockPos.ZERO, // townPos
                new JobID("test", "test"), // jobId
                0, // villagerIndex
                interval, // interval - THE KEY PARAMETER
                0, // maxState
                checks,
                (level, items) -> ImmutableList.of(), // resultGenerator
                inputs -> null, // claimSpots
                ImmutableMap.of(), // specialRules
                ImmutableList.of(), // roomPositions
                null // assignedWorkBlock
        );
    }

    /**
     * Uses reflection to get the ticksSinceLastAction field value.
     */
    private int getTicksSinceLastAction(TimeWarpWorldInteraction wi) throws Exception {
        Field field = AbstractWorldInteraction.class.getDeclaredField("ticksSinceLastAction");
        field.setAccessible(true);
        return field.getInt(wi);
    }

    /**
     * CRITICAL TEST: This test verifies the actual TimeWarpWorldInteraction.injectTicks() behavior.
     * <p>
     * On the BUGGY code (current): ticksSinceLastAction will be 1, which is < interval (50)
     * → This test will FAIL because we assert ticksSinceLastAction >= interval
     * <p>
     * On the FIXED code: ticksSinceLastAction will be 50, which is >= interval (50)
     * → This test will PASS
     */
    @Test
    void injectTicks_shouldSetTicksSinceLastActionToAtLeastInterval() throws Exception {
        int interval = 50; // Simulates bowl crafting cooldown
        int ticksPassed = 1; // Simulates consecutive warp ticks

        TimeWarpWorldInteraction wi = createMinimalTimeWarpWorldInteraction(interval);

        // Verify initial state
        int initialTicks = getTicksSinceLastAction(wi);
        Assertions.assertEquals(0, initialTicks, "Initial ticksSinceLastAction should be 0");

        // Call injectTicks with small value
        wi.injectTicks(ticksPassed);

        // Get the result
        int resultTicks = getTicksSinceLastAction(wi);

        // THE KEY ASSERTION: ticksSinceLastAction should be >= interval
        // This will FAIL on buggy code (result = 1) and PASS on fixed code (result = 50)
        Assertions.assertTrue(
                resultTicks >= interval,
                String.format(
                        "After injectTicks(%d), ticksSinceLastAction should be >= interval (%d), but was %d. " +
                        "This indicates the fix from commit 28ad8a54 has not been applied.",
                        ticksPassed, interval, resultTicks
                )
        );
    }

    /**
     * CRITICAL TEST: This test simulates the bowl warping scenario with the real injectTicks().
     * <p>
     * Each warp tick creates a new TimeWarpWorldInteraction with ticksSinceLastAction=0.
     * With the buggy code, calling injectTicks(1) leaves ticksSinceLastAction=1, which fails
     * the interval check (1 < 50).
     * <p>
     * This test counts how many times work would happen over 200 warp ticks.
     * - Buggy code: 0 work cycles (because 1 < 50 every tick)
     * - Fixed code: 200 work cycles (because 50 >= 50 every tick)
     */
    @Test
    void bowlWarping_shouldAllowWorkOnEveryWarpTick() throws Exception {
        int interval = 50;
        int totalWarpTicks = 200;
        int ticksPerWarpIteration = 1;
        int workCount = 0;

        for (int t = 0; t < totalWarpTicks; t += ticksPerWarpIteration) {
            // Simulate fresh TimeWarpWorldInteraction each tick (this is what happens during warp)
            TimeWarpWorldInteraction wi = createMinimalTimeWarpWorldInteraction(interval);

            // Call the real injectTicks method
            wi.injectTicks(ticksPerWarpIteration);

            // Check if work would happen (ticksSinceLastAction >= interval)
            int ticksSinceLastAction = getTicksSinceLastAction(wi);
            if (ticksSinceLastAction >= interval) {
                workCount++;
            }
        }

        // THE KEY ASSERTION: work should happen on EVERY tick
        // Buggy code: workCount = 0 (FAIL)
        // Fixed code: workCount = 200 (PASS)
        Assertions.assertEquals(
                totalWarpTicks,
                workCount,
                String.format(
                        "Work should happen on every warp tick (%d total), but only happened %d times. " +
                        "This indicates the fix from commit 28ad8a54 has not been applied.",
                        totalWarpTicks, workCount
                )
        );
    }

    /**
     * Baseline test: Verifies that when ticks >= interval, work happens correctly.
     * This test should pass on both buggy and fixed code.
     */
    @Test
    void injectTicks_shouldAllowWork_whenTicksGreaterOrEqualToInterval() throws Exception {
        int interval = 50;
        int ticksPassed = 100; // Greater than interval

        TimeWarpWorldInteraction wi = createMinimalTimeWarpWorldInteraction(interval);
        wi.injectTicks(ticksPassed);

        int resultTicks = getTicksSinceLastAction(wi);

        // When ticks >= interval, both implementations should allow work
        Assertions.assertTrue(
                resultTicks >= interval,
                String.format("ticksSinceLastAction (%d) should be >= interval (%d)", resultTicks, interval)
        );
    }

    // ===================================================================================
    // PART 2: Integration tests using TestWorldInteraction subclass
    // These tests demonstrate the expected behavior
    // ===================================================================================

    /**
     * A TestWorldInteraction that supports configurable interval, mimicking TimeWarpWorldInteraction.
     */
    static class IntervalAwareTestWorldInteraction extends TestWorldInteraction {
        private final int configuredInterval;

        public IntervalAwareTestWorldInteraction(
                int interval,
                ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
                ImmutableWorkStateContainer<Position, Boolean> workStatuses,
                Supplier<Claim> claim
        ) {
            super(
                    0, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(),
                    ImmutableMap.of(), ImmutableMap.of(),
                    inventory, workStatuses, claim
            );
            this.configuredInterval = interval;
        }

        public void injectTicks_buggy(int ticks) {
            ticksSinceLastAction += ticks;
        }

        public void injectTicks_fixed(int ticks) {
            ticksSinceLastAction += ticks;
            ticksSinceLastAction = Math.max(ticksSinceLastAction, configuredInterval);
        }

        public void resetTicksSinceLastAction() {
            ticksSinceLastAction = 0;
        }

        public int getTicksSinceLastAction() {
            return ticksSinceLastAction;
        }

        public boolean wouldWorkHappen() {
            return ticksSinceLastAction >= configuredInterval;
        }
    }

    @NotNull
    private static ImmutableWorkStateContainer<Position, Boolean> testWorkStateContainer() {
        HashMap<Position, State> ztate = new HashMap<>();
        return new ImmutableWorkStateContainer<>() {
            @Override
            public @Nullable State getJobBlockState(Position bp) {
                return ztate.get(bp);
            }

            @Override
            public ImmutableMap<Position, State> getAll() {
                return ImmutableMap.copyOf(ztate);
            }

            @Override
            public Boolean setJobBlockState(Position bp, State bs) {
                ztate.put(bp, bs);
                return true;
            }

            @Override
            public Boolean setJobBlockStateWithTimer(Position bp, State bs, int ticksToNextState) {
                ztate.put(bp, bs);
                return true;
            }

            @Override
            public Boolean clearState(Position bp) {
                ztate.remove(bp);
                return true;
            }

            @Override
            public boolean claimSpot(Position bp, Claim claim) {
                return true;
            }

            @Override
            public void clearClaim(Position position) {
            }

            @Override
            public boolean canClaim(Position position, Supplier<Claim> makeClaim) {
                return true;
            }
        };
    }

    private static ValidatedInventoryHandle<GathererJournalTest.TestItem> emptyInventory() {
        return ValidatedInventoryHandle.unvalidated(new InventoryHandle<GathererJournalTest.TestItem>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return ImmutableList.of(new GathererJournalTest.TestItem(""));
            }

            @Override
            public void set(int ii, GathererJournalTest.TestItem shrink) {
            }
        });
    }

    /**
     * This test demonstrates the difference between buggy and fixed behavior using TestWorldInteraction.
     */
    @Test
    void demonstrateBuggyVsFixedBehavior() {
        int interval = 50;
        int ticksPassed = 1;

        IntervalAwareTestWorldInteraction wi = new IntervalAwareTestWorldInteraction(
                interval,
                emptyInventory(),
                testWorkStateContainer(),
                () -> new Claim(UUID.randomUUID(), 100)
        );

        // Buggy behavior: ticksSinceLastAction = 1, work does NOT happen
        wi.resetTicksSinceLastAction();
        wi.injectTicks_buggy(ticksPassed);
        Assertions.assertFalse(wi.wouldWorkHappen(),
                "Buggy implementation: work should NOT happen when ticksSinceLastAction (1) < interval (50)");

        // Fixed behavior: ticksSinceLastAction = 50, work DOES happen
        wi.resetTicksSinceLastAction();
        wi.injectTicks_fixed(ticksPassed);
        Assertions.assertTrue(wi.wouldWorkHappen(),
                "Fixed implementation: work SHOULD happen when ticksSinceLastAction (50) >= interval (50)");
    }
}
