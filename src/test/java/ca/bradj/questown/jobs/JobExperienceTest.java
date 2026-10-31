package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The XP formula both lanes share (realtime {@code DeclarativeJob}, warp
 * {@code TimeWarpWorldInteraction}). Values are the ones seen in the logs.
 */
class JobExperienceTest {

    @Test
    void timedTrip_isATenthOfItsDuration() {
        // hunter short trip: work 1 at a 0-tick action, a 2000-tick timed state -> "+200 from hunter"
        Assertions.assertEquals(200, JobExperience.formula(false, List.of(1), 0, List.of(2000)));
    }

    @Test
    void workedJob_isWorkTimesActionLength_plusTheMinimum() {
        // blacksmith wooden sword: 40 work at a 25-tick action, no timed state
        Assertions.assertEquals(1001, JobExperience.formula(false, List.of(40), 25, List.of()));
    }

    @Test
    void noWorkNoTime_isTheMinimumOne() {
        // every cook/baker job ("work": 0.1 parses to 0): "+1 from cook"
        Assertions.assertEquals(1, JobExperience.formula(false, List.of(0), 50, List.of()));
    }

    @Test
    void noExperienceRule_isZero() {
        Assertions.assertEquals(0, JobExperience.formula(true, List.of(40), 25, List.of(2000)));
    }
}
