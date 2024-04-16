package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.Item;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.Worker;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class VillagerPreferredWork {


    public static <RESULT> JobID compute(
            UUID uuid,
            @Nullable Object worker,
            Function<JobID, Collection<JobID>> getPreferredWork,
            Predicate<JobID> canFit,
            Predicate<JobID> canAlwaysWork,
            Collection<RESULT> requestedResults,
            BiPredicate<JobID, RESULT> canSatisfy
    ) {
        if (worker == null) {
            QT.BLOCK_LOGGER.error("No entities found for UUID: {}", uuid);
            return null;
        }
        if (!(worker instanceof Worker v)) {
            QT.BLOCK_LOGGER.error("Entity is wrong type: {}", worker);
            return null;
        }

        List<JobID> preference = new ArrayList<>(getPreferredWork.apply(v.getJobId()));

        // TODO[TEST]: Allow work to be "claimed" so that if there are multiple
        //  requests that can be satisfied by one job, the villagers with that
        //  job will distribute themselves across those requests.

        // For now, we use randomization to give work requests a fair chance of being selected
        Collections.shuffle(preference);

        // TODO[ASAP]: Use a job attempt counter to determine which preference they choose
        //  With full random, the villager could theoretically never choose a job that
        //  is possible with the items currently in town. Under true random, they could
        //  potentially just keep choosing "gather with axe" over and over while there
        //  are no axes in town, without trying "gather with shovel" while there IS a
        //  shovel in town. Using a counter would allow the villager to consider every
        //  job option.

        for (JobID p : preference) {
            if (!canFit.test(p)) {
                QT.FLAG_LOGGER.debug(
                        "Villager will not do {} because there is not enough time left in the day: {}", p, uuid);
                continue;
            }

            if (canAlwaysWork.test(p)) {
                return p;
            }
            for (RESULT requestedResult : requestedResults) {
                // TODO: Think about how work chains work.
                //  E.g. If a blacksmith needs iron ingots to do a requested job,
                //  but none of the other villagers produce that resource, the
                //  blacksmith should light up red to indicate a broken chain and
                //  that the player will need to contribute in order for the
                //  blacksmith to work, rather than everything being automated.
                if (canSatisfy.test(p, requestedResult)) {
                    return p;
                }
            }
        }
        return null;
    }

    public static <ITEM extends Item<ITEM>> boolean canSatisfy(
            JobID jobId,
            Predicate<JobID> isSeekingWork,
            Predicate<JobID> jobExists,
            Collection<ITEM> jobResults,
            Predicate<ITEM> isSameItem
    ) {
        if (isSeekingWork.test(jobId)) {
            return false;
        }

        if (!jobExists.test(jobId)) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", jobExists);
            return false;
        }
        for (ITEM r : jobResults) {
            if (isSameItem.test(r)) {
                return true;
            }
        }
        return false;
    }
}
