package ca.bradj.questown.jobs;

import ca.bradj.questown.town.Warper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

class ComputeWarpTicksTest {

    @Test
    void shouldReturnEmptyForZeroTicksPassed() {
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 0, 50);

        // With 0 ticks passed, only the starting tick should be included
        Assertions.assertEquals(1, ticks.size());
    }

    @Test
    void shouldStartAtReferenceTick() {
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(1000, 500, 50);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        Assertions.assertEquals(1000, tickList.get(0).tick());
    }

    @Test
    void shouldUseMinimumStepIntervalOf100() {
        // With workInterval=10, doubled=20, but minimum is 100
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 300, 10);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 100, 200, 300 = 4 ticks
        Assertions.assertEquals(4, tickList.size());
        Assertions.assertEquals(0, tickList.get(0).tick());
        Assertions.assertEquals(100, tickList.get(1).tick());
        Assertions.assertEquals(200, tickList.get(2).tick());
        Assertions.assertEquals(300, tickList.get(3).tick());
    }

    @Test
    void shouldUseDoubledWorkIntervalWhenLargerThanMinimum() {
        // With workInterval=100, doubled=200, which is > 100 minimum
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 600, 100);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 200, 400, 600 = 4 ticks
        Assertions.assertEquals(4, tickList.size());
        Assertions.assertEquals(0, tickList.get(0).tick());
        Assertions.assertEquals(200, tickList.get(1).tick());
        Assertions.assertEquals(400, tickList.get(2).tick());
        Assertions.assertEquals(600, tickList.get(3).tick());
    }

    @Test
    void shouldSetTicksSincePreviousToStepInterval() {
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 200, 100);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // workInterval=100, doubled=200, stepInterval=200
        for (Warper.Tick tick : tickList) {
            Assertions.assertEquals(200, tick.ticksSincePrevious());
        }
    }

    @Test
    void shouldNotExceedMaxTick() {
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 150, 10);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // stepInterval=100, so: 0, 100 (150 is not included because next would be 200)
        Assertions.assertEquals(2, tickList.size());
        Assertions.assertEquals(0, tickList.get(0).tick());
        Assertions.assertEquals(100, tickList.get(1).tick());
    }

    @Test
    void shouldIncludeMaxTickWhenExactlyReachable() {
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 200, 10);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // stepInterval=100, so: 0, 100, 200 = 3 ticks
        Assertions.assertEquals(3, tickList.size());
        Assertions.assertEquals(200, tickList.get(2).tick());
    }

    @Test
    void shouldHandleLargeTicksPassed() {
        // Simulate warping a full Minecraft day (24000 ticks)
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 24000, 50);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // stepInterval = max(50*2, 100) = 100
        // 24000 / 100 + 1 = 241 ticks
        Assertions.assertEquals(241, tickList.size());
        Assertions.assertEquals(0, tickList.get(0).tick());
        Assertions.assertEquals(24000, tickList.get(240).tick());
    }

    // Timer constraint tests (4-parameter variant)

    @Test
    void shouldConstrainStepIntervalToTimerValue_whenSmallerThanWalkHeuristic() {
        // workInterval=200, walkHeuristic=400, but timer=150
        // stepInterval = max(min(400, 150), 100) = 150
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 600, 200, 150);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 150, 300, 450, 600 = 5 ticks
        Assertions.assertEquals(5, tickList.size());
        Assertions.assertEquals(0, tickList.get(0).tick());
        Assertions.assertEquals(150, tickList.get(1).tick());
        Assertions.assertEquals(300, tickList.get(2).tick());
        Assertions.assertEquals(450, tickList.get(3).tick());
        Assertions.assertEquals(600, tickList.get(4).tick());
    }

    @Test
    void shouldUseWalkHeuristic_whenTimerLargerThanWalkHeuristic() {
        // workInterval=100, walkHeuristic=200, timer=500
        // stepInterval = max(min(200, 500), 100) = 200
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 400, 100, 500);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 200, 400 = 3 ticks (same as without timer)
        Assertions.assertEquals(3, tickList.size());
        Assertions.assertEquals(200, tickList.get(1).tick());
    }

    @Test
    void shouldEnforceMinimum_whenTimerSmallerThanMinimum() {
        // workInterval=10, walkHeuristic=20, timer=50
        // stepInterval = max(min(20, 50), 100) = 100
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 200, 10, 50);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 100, 200 = 3 ticks (minimum enforced)
        Assertions.assertEquals(3, tickList.size());
        Assertions.assertEquals(100, tickList.get(1).tick());
    }

    @Test
    void shouldIgnoreTimerValue_whenZero() {
        // workInterval=100, walkHeuristic=200, timer=0
        // Timer of 0 should be treated as null
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 400, 100, 0);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 200, 400 = 3 ticks (same as without timer)
        Assertions.assertEquals(3, tickList.size());
        Assertions.assertEquals(200, tickList.get(1).tick());
    }

    @Test
    void shouldIgnoreTimerValue_whenNull() {
        // workInterval=100, walkHeuristic=200, timer=null
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 400, 100, null);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // 0, 200, 400 = 3 ticks (same as 3-param version)
        Assertions.assertEquals(3, tickList.size());
        Assertions.assertEquals(200, tickList.get(1).tick());
    }

    @Test
    void shouldSetTicksSincePreviousToConstrainedInterval() {
        // Verify ticksSincePrevious reflects the timer-constrained interval
        Collection<Warper.Tick> ticks = DeclarativeJobs.computeWarpTicks(0, 300, 200, 150);

        List<Warper.Tick> tickList = List.copyOf(ticks);
        // stepInterval = 150
        for (Warper.Tick tick : tickList) {
            Assertions.assertEquals(150, tick.ticksSincePrevious());
        }
    }
}
