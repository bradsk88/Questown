package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** The XP formula. */
class JobExperienceTest {

    @Test
    void timedTrip_isATenthOfItsDuration() {
        Assertions.assertEquals(200, JobExperience.formula(false, List.of(1), 0, List.of(2000)));
    }

    @Test
    void workedJob_isWorkTimesActionLength_plusTheMinimum() {
        Assertions.assertEquals(1001, JobExperience.formula(false, List.of(40), 25, List.of()));
    }

    @Test
    void noWorkNoTime_isTheMinimumOne() {
        Assertions.assertEquals(1, JobExperience.formula(false, List.of(0), 50, List.of()));
    }

    @Test
    void noExperienceRule_isZero() {
        Assertions.assertEquals(0, JobExperience.formula(true, List.of(40), 25, List.of(2000)));
    }
}
