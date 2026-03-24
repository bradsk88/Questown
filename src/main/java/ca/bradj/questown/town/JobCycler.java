package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class JobCycler {

    private List<JobID> shuffled = new ArrayList<>();
    private Set<JobID> knownJobs = new HashSet<>();
    private int index = 0;

    public @Nullable JobID nextValid(
            Iterable<JobID> availableJobs,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        Set<JobID> currentSet = toSet(availableJobs);
        if (!currentSet.equals(knownJobs)) {
            reshuffleFrom(currentSet);
        }

        if (shuffled.isEmpty()) {
            return null;
        }

        int size = shuffled.size();
        for (int i = 0; i < size; i++) {
            int pos = (index + i) % size;
            JobID candidate = shuffled.get(pos);
            if (isValid(candidate, canAlwaysStart, canFitInDay, requestedResults, td)) {
                index = (pos + 1) % size;
                return candidate;
            }
        }

        reshuffleFrom(currentSet);
        return null;
    }

    private static boolean isValid(
            JobID candidate,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        if (!canAlwaysStart.test(candidate) && !canFitInDay.test(candidate)) {
            return false;
        }
        if (requestedResults.isEmpty()) {
            return true;
        }
        for (WorkRequest request : requestedResults) {
            Ingredient ingredient = request.asIngredient();
            if (ServerJobsRegistry.canSatisfy(td, candidate, ingredient)) {
                return true;
            }
        }
        return false;
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
