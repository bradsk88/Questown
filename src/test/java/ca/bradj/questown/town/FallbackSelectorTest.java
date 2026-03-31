package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.WorksBehaviour;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackSelectorTest {

    private TownVillagerData.FallbackSelector selector;
    private final JobID jobA = new JobID("test", "a");
    private final JobID jobB = new JobID("test", "b");
    private final JobID jobC = new JobID("test", "c");

    @BeforeEach
    void setUp() {
        selector = new TownVillagerData.FallbackSelector();
    }

    @Test
    void buffersUntilThresholdReached() {
        // With increment 10, need 10 calls to reach 100
        for (int i = 0; i < 9; i++) {
            JobID result = selector.tryFallback(
                    10, jobA,
                    id -> true, id -> true,
                    ImmutableList.of(),
                    null,
                    ImmutableList.of(jobA),
                    ids -> ImmutableList.copyOf(ids)
            );
            assertNull(result, "Should buffer on call " + (i + 1));
            assertTrue(selector.isBuffering());
        }
    }

    @Test
    void stopsBufferingAtThreshold() {
        // Fill buffer to 100 in one shot
        JobID result = selector.tryFallback(
                100, jobA,
                id -> true, id -> true,
                ImmutableList.of(),
                null,
                ImmutableList.of(jobB),
                ids -> ImmutableList.copyOf(ids)
        );
        // Should not be buffering anymore and should return a job
        assertFalse(selector.isBuffering());
        assertNotNull(result);
    }

    @Test
    void returnsFirstFittingPreselectedJob() {
        // Skip getPreferredWork (no requested results, so it would need ServerJobsRegistry)
        // Instead test the preselected fallback path
        JobID result = selector.tryFallback(
                100, jobA,
                id -> id.equals(jobC), // only jobC fits
                id -> false,
                ImmutableList.of(),
                null,
                ImmutableList.of(jobA, jobB, jobC),
                ids -> ImmutableList.copyOf(ids)
        );
        assertEquals(jobC, result);
    }

    @Test
    void returnsNullWhenNothingFits() {
        JobID result = selector.tryFallback(
                100, jobA,
                id -> false, // nothing fits
                id -> false,
                ImmutableList.of(),
                null,
                ImmutableList.of(jobA, jobB),
                ids -> ImmutableList.copyOf(ids)
        );
        assertNull(result);
        assertFalse(selector.isBuffering());
    }

    @Test
    void resetsBufferAfterFiring() {
        // Fill to threshold
        selector.tryFallback(
                100, jobA,
                id -> true, id -> true,
                ImmutableList.of(),
                null,
                ImmutableList.of(jobA),
                ids -> ImmutableList.copyOf(ids)
        );
        assertFalse(selector.isBuffering());

        // Should buffer again from zero
        JobID result = selector.tryFallback(
                10, jobA,
                id -> true, id -> true,
                ImmutableList.of(),
                null,
                ImmutableList.of(jobA),
                ids -> ImmutableList.copyOf(ids)
        );
        assertNull(result);
        assertTrue(selector.isBuffering());
    }

    @Test
    void bufferAccumulatesAcrossCalls() {
        // 50 + 50 = 100, should fire on second call
        assertNull(selector.tryFallback(
                50, jobA, id -> true, id -> true,
                ImmutableList.of(), null,
                ImmutableList.of(jobA), ids -> ImmutableList.copyOf(ids)
        ));
        assertTrue(selector.isBuffering());

        JobID result = selector.tryFallback(
                50, jobA, id -> true, id -> true,
                ImmutableList.of(), null,
                ImmutableList.of(jobB), ids -> ImmutableList.copyOf(ids)
        );
        assertNotNull(result);
        assertFalse(selector.isBuffering());
    }
}
