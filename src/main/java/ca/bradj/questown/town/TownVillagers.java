package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class TownVillagers {
    public static @Nullable JobID getPreferredWork(
            JobID villagerCurrentJob,
            Predicate<JobID> canFitInDay,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        List<JobID> preference = new ArrayList<>(JobsRegistry.getPreferredWorkIds(villagerCurrentJob));

        // TODO: [TEST] Allow work to be "claimed" so that if there are multiple
        //  requests that can be satisfied by one job, the villagers with that
        //  job will distribute themselves across those requests.

        // For now, we use randomization to give work requests a fair chance of being selected
        Collections.shuffle(preference);

        // TODO: [ASAP] Use a job attempt counter to determine which preference they choose
        //  With full random, the villager could theoretically never choose a job that
        //  is possible with the items currently in town. Under true random, they could
        //  potentially just keep choosing "gather with axe" over and over while there
        //  are no axes in town, without trying "gather with shovel" while there IS a
        //  shovel in town. Using a counter would allow the villager to consider every
        //  job option.

        for (JobID p : preference) {
            if (!canFitInDay.test(p)) {
                QT.FLAG_LOGGER.debug(
                        "Villager will not do {} because there is not enough time left in the day", p);
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
                if (JobsRegistry.canSatisfy(td, p, requestedResult)) {
                    return p;
                }
            }
        }
        return null;
    }
}
