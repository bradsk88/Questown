package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class JobScoreBandsTest {

    private static final double PREFERRED = 0.65;
    private static final double MIN = 0.25;

    private record Job(String id, double score) {
    }

    private static List<String> band(Job... jobs) {
        return JobScoreBands.band(List.of(jobs), Job::score, PREFERRED, MIN).stream().map(Job::id).toList();
    }

    @Test
    void alwaysConsiderScore_sitsBetweenTheTwoBands() {
        double s = JobScoreBands.alwaysConsiderScore(PREFERRED, MIN);
        Assertions.assertTrue(s > MIN, "must stay in the minimum band: " + s);
        Assertions.assertTrue(s <= PREFERRED, "must stay out of the preferred band: " + s);
    }

    @Test
    void readyFoodJob_beatsAStockJob() {
        // The coin flip: cook/stock_fuel tied a ready food job at 1.0 and the shuffle chose.
        double stock = JobScoreBands.alwaysConsiderScore(PREFERRED, MIN);
        Assertions.assertEquals(List.of("beef"), band(new Job("stock_fuel", stock), new Job("beef", 1.0)));
    }

    @Test
    void stockJobStillRuns_whenNoRealWorkIsReady() {
        double stock = JobScoreBands.alwaysConsiderScore(PREFERRED, MIN);
        Assertions.assertEquals(
                List.of("stock_fuel", "stock_ingredients"),
                band(new Job("stock_fuel", stock), new Job("stock_ingredients", stock), new Job("beef", 0.0))
        );
    }

    @Test
    void band_isEmpty_whenNothingClearsTheMinimum() {
        Assertions.assertEquals(List.of(), band(new Job("beef", 0.1)));
    }
}
