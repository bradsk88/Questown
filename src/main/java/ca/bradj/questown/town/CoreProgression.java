package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class CoreProgression<JOB, ROOM> {

    private final Predicate<JOB> isGatherer;
    private final Function<Collection<JOB>, JOB> chooseRandom;
    private final Predicate<JOB> generatesVillagerFood;
    private final Predicate<JOB> generatesNeededResources;

    public CoreProgression(
            Predicate<JOB> isGatherer,
            Function<Collection<JOB>, JOB> chooseRandom,
            Predicate<JOB> generatesVillagerFood,
            Predicate<JOB> generatesNeededResources
    ) {
        this.isGatherer = isGatherer;
        this.chooseRandom = chooseRandom;
        this.generatesVillagerFood = generatesVillagerFood;
        this.generatesNeededResources = generatesNeededResources;
    }

    public @Nullable JOB getFirstJobChange(
            Collection<JOB> allRootJobs,
            Function<JOB, ROOM> getRoom,
            Predicate<ROOM> isQuestExisting
    ) {
        // TODO: Check if allRoots empty

        // Grab all the jobs which are not "gatherer"
        List<JOB> allRoots = ImmutableList.copyOf(allRootJobs);
        Collection<JOB> firstLayerJobs = allRoots
                .stream()
                .filter(Predicate.not(isGatherer))
                .toList();

        if (firstLayerJobs.isEmpty()) {
            return null;
        }

        // Prefer jobs with simple rooms that don't yet have a quest
        ImmutableMap<JOB, ROOM> roomsNeeded = UtilClean.toMap(firstLayerJobs, getRoom);

        Predicate<ROOM> isQuestAddable = Predicate.not(isQuestExisting);
        ImmutableMap<JOB, ROOM> roomsWithoutQuest = UtilClean.filterByValue(roomsNeeded, isQuestAddable);

        if (roomsWithoutQuest.isEmpty()) {
            return chooseRandom.apply(firstLayerJobs);
        }

        // Prefer food-producing jobs (during early progression)
        // TODO: At what point should we stop prioritizing food?
        List<JOB> foodProducing = roomsWithoutQuest.keySet().stream().filter(generatesVillagerFood).toList();
        if (!foodProducing.isEmpty()) {
            return chooseRandom.apply(foodProducing);
        }

        // Prefer jobs that meet the needs of the town
        List<JOB> resourceProducing = roomsWithoutQuest.keySet().stream().filter(generatesNeededResources).toList();
        if (!resourceProducing.isEmpty()) {
            return chooseRandom.apply(resourceProducing);
        }

        return chooseRandom.apply(roomsWithoutQuest.keySet());
    }

    public CoreProgression<JOB, ROOM> withFoodCheck(Predicate<JOB> o) {
        return new CoreProgression<>(isGatherer, chooseRandom, o, generatesNeededResources);
    }

    public CoreProgression<JOB, ROOM> withResourceCheck(Predicate<JOB> o) {
        return new CoreProgression<>(isGatherer, chooseRandom, generatesVillagerFood, o);
    }
}
