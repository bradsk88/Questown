package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VillagerLearningHandleTest {

    UUID villager1 = UUID.randomUUID();

    @Test
    public void testShouldPrepareEmptyListIfNoMoreJobsAvailable() {
        ImmutableSet<String> allJobs = ImmutableSet.of("chop", "dice", "julienne");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> ImmutableList.copyOf(allJobs),
                (j1, j2) -> true,
                (j1) -> true,
                2
        );
        h.init(allJobs); // All jobs already known
        h.tick(ImmutableList.of("prep cook"));
        assertTrue(h.getNextJobAwareness("prep cook").isEmpty());
    }

    @Test
    public void testShouldPrepareListOfOneIfOneJobAlreadyKnownAndIsPresentAtBeginningOfShuffledSet() {
        // During the tick, we shuffle "all jobs" and take the first 2 items
        // Those first two get queued up to be added to the town's knowledge
        // We filter out any jobs that are already present in the knowledge
        // to make this easier to test and debug.

        ImmutableSet<String> allJobs = ImmutableSet.of("chop", "dice", "julienne");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> ImmutableList.of("chop", "dice", "julienne"),
                (j1, j2) -> true,
                (j1) -> true,
                2
        );
        h.init(ImmutableList.of("chop"));
        h.tick(ImmutableList.of("prep cook"));
        assertIterableEquals(ImmutableList.of("dice"), h.getNextJobAwareness("prep cook"));
    }

    @Test
    public void testShouldPrepareListOfTwoIfOneJobAlreadyKnownButIsAbsentFromBeginningOfShuffledSet() {

        ImmutableSet<String> allJobs = ImmutableSet.of("chop", "dice", "julienne");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> ImmutableList.of("dice", "julienne", "chop"),
                (j1, j2) -> true,
                (j1) -> true,
                2
        );
        h.init(ImmutableList.of("chop"));
        h.tick(ImmutableList.of("prep cook"));
        assertIterableEquals(ImmutableList.of("dice", "julienne"), h.getNextJobAwareness("prep cook"));
    }

    @Test
    public void testShouldReturnCorrectListsForDifferentJobsDifferentRoots() {
        ImmutableSet<String> allJobs = ImmutableSet.of(
                "cook:chop",
                "cook:dice",
                "cook:julienne",
                "hunter:deer",
                "hunter:rabbits",
                "hunter:birds"
        );
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> ImmutableList.of(
                        "cook:dice",
                        "cook:julienne",
                        "cook:chop",
                        "hunter:deer",
                        "hunter:rabbits",
                        "hunter:birds"
                ),
                (j1, j2) -> j1.split(":")[0].equals(j2.split(":")[0]),
                (j1) -> true,
                2
        );
        h.init(ImmutableList.of("cook:chop", "hunter:rabbits")); // These jobs are already known
        h.tick(ImmutableList.of("cook:steak", "hunter:bison")); // These are the villager's current jobs
        assertIterableEquals(ImmutableList.of("cook:dice", "cook:julienne"), h.getNextJobAwareness("cook:steak"));
        assertIterableEquals(ImmutableList.of("hunter:deer"), h.getNextJobAwareness("hunter:bison"));
    }

    @Test
    public void testShouldReturnCorrectListsForDifferentJobSameRoot() {

        ImmutableSet<String> allJobs = ImmutableSet.of("cook:chop", "cook:dice", "cook:julienne");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> ImmutableList.of("cook:dice", "cook:julienne", "cook:chop"),
                (j1, j2) -> j1.split(":")[0].equals(j2.split(":")[0]),
                (j) -> true,
                2
        );
        h.init(ImmutableList.of("cook:chop"));
        h.tick(ImmutableList.of("cook:steak", "cook:rice"));
        assertIterableEquals(ImmutableList.of("cook:dice", "cook:julienne"), h.getNextJobAwareness("cook:steak"));
        assertIterableEquals(ImmutableList.of("cook:dice", "cook:julienne"), h.getNextJobAwareness("cook:rice"));
    }

    @Test
    public void testShouldReturnCorrectListsForDifferentJobSameRootDifferentSubtree() {

        ImmutableList<String> allJobs = ImmutableList.of("a", "b", "aa", "ab", "ba", "bb", "c");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> ImmutableSet.copyOf(allJobs),
                j -> allJobs,
                (j1, j2) -> j2.startsWith(j1) && j2.length() != j1.length(),
                (j) -> true,
                // a and b are unlocked
                2
        );
        h.init(ImmutableList.of());
        h.tick(ImmutableList.of("a", "b"));
        @NotNull Set<String> all = h.getNextJobAwarenesses().entrySet().stream()
                                    .flatMap(v -> v.getValue().stream())
                                    .collect(Collectors.toSet());
        assertEquals(ImmutableSet.of("aa", "ab", "ba", "bb"), all);
    }

    // FIXME: Test same root different sub-trees
    //  E.g. fishing gatherer and shovel gatherer should generate 4 potential "awarenesses"

    @Test
    public void testShouldNotRecomputeOrReplaceIfAlreadyComputed() {

        ImmutableSet<String> allJobs = ImmutableSet.of("cook:a", "cook:b", "cook:c", "cook:d");
        AtomicBoolean secondAttempt = new AtomicBoolean();
        ImmutableList<String> shuffle1 = ImmutableList.of("cook:a", "cook:b", "cook:c", "cook:d");
        ImmutableList<String> shuffle2 = ImmutableList.of("cook:d", "cook:c", "cook:b", "cook:a");
        VillagerLearningHandle<String> h = new VillagerLearningHandle<>(
                () -> allJobs,
                j -> !secondAttempt.get() ? shuffle1 : shuffle2,
                (j1, j2) -> j1.split(":")[0].equals(j2.split(":")[0]),
                (j1) -> true,
                2
        );
        h.init(ImmutableList.of("cook:chop"));
        h.tick(ImmutableList.of("cook:steak"));
        ImmutableList<String> expected = ImmutableList.of("cook:a", "cook:b");
        assertIterableEquals(expected, h.getNextJobAwareness("cook:steak"));
        secondAttempt.set(true);
        h.tick(ImmutableList.of("cook:steak"));
        assertIterableEquals(expected, h.getNextJobAwareness("cook:steak"));
    }

}