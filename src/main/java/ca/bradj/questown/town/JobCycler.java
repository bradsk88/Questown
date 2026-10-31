package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.requests.BoardRequests;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

public class JobCycler {

    private List<JobID> shuffled = new ArrayList<>();
    private Set<JobID> knownJobs = new HashSet<>();
    private int index = 0;

    public @Nullable JobID nextValid(
            Iterable<JobID> availableJobs,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            BoardRequests requestedResults,
            WorksBehaviour.TownData td
    ) {
        return nextValid(availableJobs, canAlwaysStart, canFitInDay, requestedResults, td, j -> 0L);
    }

    public @Nullable JobID nextValid(
            Iterable<JobID> availableJobs,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            BoardRequests requestedResults,
            WorksBehaviour.TownData td,
            ToLongFunction<JobID> tripLength
    ) {
        return nextValid(availableJobs, canAlwaysStart, canFitInDay, requestedResults, td, tripLength, j -> 0);
    }

    /**
     * {@code tripLength} ranks the valid jobs: the longest trip that fits the day wins (dev playtest
     * 2026-10-29: "Longest that fits the day"). Then {@code level}: the job needing the highest
     * villager level wins (dev playtest 2026-10-30: "Crafter should prefer building the highest
     * level thing", for every villager). Remaining ties keep the shuffled rotation.
     */
    public @Nullable JobID nextValid(
            Iterable<JobID> availableJobs,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            BoardRequests requestedResults,
            WorksBehaviour.TownData td,
            ToLongFunction<JobID> tripLength,
            ToIntFunction<JobID> level
    ) {
        Set<JobID> currentSet = toSet(availableJobs);
        if (!currentSet.equals(knownJobs)) {
            reshuffleFrom(currentSet);
        }

        if (shuffled.isEmpty()) {
            return null;
        }

        int size = shuffled.size();
        int bestPos = -1;
        long bestLength = -1;
        int bestLevel = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            int pos = (index + i) % size;
            JobID candidate = shuffled.get(pos);
            if (!isValid(candidate, canAlwaysStart, canFitInDay, requestedResults, td)) {
                continue;
            }
            long length = tripLength.applyAsLong(candidate);
            int lvl = level.applyAsInt(candidate);
            if (length > bestLength || (length == bestLength && lvl > bestLevel)) {
                bestPos = pos;
                bestLength = length;
                bestLevel = lvl;
            }
        }
        if (bestPos >= 0) {
            index = (bestPos + 1) % size;
            return shuffled.get(bestPos);
        }

        reshuffleFrom(currentSet);
        return null;
    }

    private static boolean isValid(
            JobID candidate,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            BoardRequests requestedResults,
            WorksBehaviour.TownData td
    ) {
        if (!canAlwaysStart.test(candidate) && !canFitInDay.test(candidate)) {
            return false;
        }
        if (requestedResults.isEmpty()) {
            return true;
        }
        return ServerJobsRegistry.firstAnswerable(td, candidate, requestedResults).isPresent();
    }

    private void reshuffleFrom(Set<JobID> jobs) {
        knownJobs = new HashSet<>(jobs);
        shuffled = new ArrayList<>(jobs);
        Collections.shuffle(shuffled);
        index = 0;
    }

    private static Set<JobID> toSet(Iterable<JobID> jobs) {
        Set<JobID> set = new HashSet<>();
        for (JobID job : jobs) {
            set.add(job);
        }
        return set;
    }
}
