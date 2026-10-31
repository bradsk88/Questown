package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.requests.BoardRequests;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dev playtest 2026-10-29: "hunter is preferring to work shorter jobs" -> longest trip that fits. */
class JobCyclerTest {

    private static final JobID SHORT = new JobID("hunter", "sword");
    private static final JobID HALF = new JobID("hunter", "sword_half_day");
    private static final JobID FULL = new JobID("hunter", "sword_full_day");
    private static final Map<JobID, Long> LENGTH = Map.of(SHORT, 2000L, HALF, 6000L, FULL, 12000L);
    private static final BoardRequests NONE = BoardRequests.unpaused(ImmutableList.of());

    private static JobID pick(JobCycler c, Set<JobID> fits) {
        return c.nextValid(List.of(SHORT, HALF, FULL), j -> false, fits::contains, NONE, null,
                j -> LENGTH.getOrDefault(j, 0L));
    }

    @Test
    void theLongestTripThatFits_isChosen_everyTime() {
        JobCycler c = new JobCycler();
        for (int i = 0; i < 6; i++) {
            Assertions.assertEquals(FULL, pick(c, Set.of(SHORT, HALF, FULL)));
        }
    }

    @Test
    void lateInTheDay_theLongestThatStillFits() {
        JobCycler c = new JobCycler();
        Assertions.assertEquals(HALF, pick(c, Set.of(SHORT, HALF)));
        Assertions.assertEquals(SHORT, pick(c, Set.of(SHORT)));
    }

    @Test
    void jobsThatAreNotTrips_keepRotating() {
        JobCycler c = new JobCycler();
        JobID a = new JobID("cook", "mutton");
        JobID b = new JobID("cook", "beef");
        JobID first = c.nextValid(List.of(a, b), j -> true, j -> true, NONE, null, j -> 0L);
        JobID second = c.nextValid(List.of(a, b), j -> true, j -> true, NONE, null, j -> 0L);
        Assertions.assertNotEquals(first, second);
    }

    /** Dev playtest 2026-10-30: "Crafter should prefer building the highest level thing" (every villager). */
    @Test
    void amongInTownJobs_theHighestLevelWins_everyTime() {
        JobCycler c = new JobCycler();
        JobID stick = new JobID("crafter", "stick");
        JobID boots = new JobID("crafter", "leather_boots");
        JobID sword = new JobID("crafter", "stone_sword");
        Map<JobID, Integer> level = Map.of(stick, 1, boots, 3, sword, 5);
        for (int i = 0; i < 4; i++) {
            Assertions.assertEquals(sword, c.nextValid(List.of(stick, boots, sword), j -> true, j -> true, NONE, null,
                    j -> 0L, level::get));
        }
    }

    @Test
    void aLongerTrip_beatsAHigherLevel() {
        JobCycler c = new JobCycler();
        Map<JobID, Integer> level = Map.of(SHORT, 9, HALF, 1, FULL, 1);
        Assertions.assertEquals(FULL, c.nextValid(List.of(SHORT, HALF, FULL), j -> false, j -> true, NONE, null,
                j -> LENGTH.getOrDefault(j, 0L), level::get));
    }
}
