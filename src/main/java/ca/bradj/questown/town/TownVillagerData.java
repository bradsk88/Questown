package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class TownVillagerData {
    public static @Nullable JobID getPreferredWork(
            JobID villagerCurrentJob,
            Predicate<JobID> canFitInDay,
            Predicate<JobID> canAlwaysStart,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        List<JobID> preference = new ArrayList<>(ServerJobsRegistry.getPreferredWorkIds(villagerCurrentJob));

        return chooseFromList(canFitInDay, canAlwaysStart, requestedResults, td, preference);
    }

    public static @Nullable JobID chooseFromList(
            Predicate<JobID> canFitInDay,
            Predicate<JobID> canAlwaysStart,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td,
            Collection<JobID> preferenceIn
    ) {
        // TODO: [TEST] Allow work to be "claimed" so that if there are multiple
        //  requests that can be satisfied by one job, the villagers with that
        //  job will distribute themselves across those requests.

        List<JobID> preference = new ArrayList<>(preferenceIn);

        // For now, we use randomization to give work requests a fair chance of being selected
        Collections.shuffle(preference);

        for (JobID p : preference) {
            if (canAlwaysStart.test(p)) {
                return p;
            }
            if (!canFitInDay.test(p)) {
                continue;
            }

            List<Ingredient> i = requestedResults.stream()
                                                 .map(WorkRequest::asIngredient)
                                                 .toList();
            for (Ingredient requestedResult : i) {
                // TODO: Think about how work chains work.
                //  E.g. If a blacksmith needs iron ingots to do a requested job,
                //  but none of the other villagers produce that resource, the
                //  blacksmith should light up red to indicate a broken chain and
                //  that the player will need to contribute in order for the
                //  blacksmith to work, rather than everything being automated.
                if (ServerJobsRegistry.canSatisfy(td, p, requestedResult)) {
                    return p;
                }
            }
        }
        return null;
    }
}
