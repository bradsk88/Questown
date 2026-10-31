package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-10-17 playtest: a job change named an unregistered id (the simple-furnace template's
 * source id cook/simple_furnace_food), and ServerJobsRegistry's "any job in the same root"
 * fallback took the FIRST cook job - cook/stock_ingredients, which needs a stocked kitchen.
 * User ruling: "stock kitchen should not be a job that gets unlocked initially". The
 * fallback now prefers a job unlocked with the root ({@link Work#isUnlockedInitially}).
 */
class WorkSameRootFallbackTest {

    private record Candidate(String id, @Nullable JobID parent, boolean unlockedWithRoot) {
    }

    private static final JobID EXTRACT = new JobID("cook", "extract");

    private static Optional<Candidate> pick(List<Candidate> cs) {
        return Work.preferInitiallyUnlocked(cs, Candidate::parent, Candidate::unlockedWithRoot);
    }

    @Test
    void prefersTheProgressionRootOverAChildListedFirst() {
        List<Candidate> cs = List.of(
                new Candidate("cook/stock_ingredients", EXTRACT, false),
                new Candidate("cook/extract", null, false)
        );
        assertEquals("cook/extract", pick(cs).orElseThrow().id());
    }

    @Test
    void prefersAnUnlockedWithRootChildOverALockedOne() {
        List<Candidate> cs = List.of(
                new Candidate("cook/stock_ingredients", EXTRACT, false),
                new Candidate("cook/fuel", EXTRACT, true)
        );
        assertEquals("cook/fuel", pick(cs).orElseThrow().id());
    }

    @Test
    void fallsBackToTheFirstWhenNoneIsInitiallyUnlocked() {
        List<Candidate> cs = List.of(
                new Candidate("cook/stock_ingredients", EXTRACT, false),
                new Candidate("cook/stock_fuel", EXTRACT, false)
        );
        assertEquals("cook/stock_ingredients", pick(cs).orElseThrow().id());
    }

    @Test
    void emptyIsEmpty() {
        assertEquals(Optional.empty(), pick(List.of()));
    }
}
